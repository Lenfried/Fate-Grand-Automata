package com.mathewsachin.fategrandautomata.scripts.entrypoints

import com.mathewsachin.fategrandautomata.core.*
import com.mathewsachin.fategrandautomata.scripts.ImageLocator
import com.mathewsachin.fategrandautomata.scripts.enums.GameServerEnum
import com.mathewsachin.fategrandautomata.scripts.enums.RefillResourceEnum
import com.mathewsachin.fategrandautomata.scripts.modules.*
import com.mathewsachin.fategrandautomata.scripts.prefs.Preferences
import kotlin.time.seconds

// Checks if Support Selection menu is up
fun isInSupport(): Boolean {
    return Game.SupportScreenRegion.exists(ImageLocator.SupportScreen, Similarity = 0.85)
}

class AutoBattle : EntryPoint() {
    private val support = Support()
    private val card = Card()
    private val battle = Battle()
    private val autoSkill = AutoSkill()

    private var stonesUsed = 0
    private var isContinuing = false

    private fun refillStamina() {
        val refillPrefs = Preferences.Refill

        if (refillPrefs.enabled && stonesUsed < refillPrefs.repetitions) {
            when (refillPrefs.resource) {
                RefillResourceEnum.SQ -> Game.StaminaSqClick.click()
                RefillResourceEnum.AllApples -> {
                    Game.StaminaBronzeClick.click()
                    Game.StaminaSilverClick.click()
                    Game.StaminaGoldClick.click()
                }
                RefillResourceEnum.Gold -> Game.StaminaGoldClick.click()
                RefillResourceEnum.Silver -> Game.StaminaSilverClick.click()
                RefillResourceEnum.Bronze -> Game.StaminaBronzeClick.click()
            }

            AutomataApi.wait(1.seconds)
            Game.StaminaOkClick.click()
            ++stonesUsed

            AutomataApi.wait(3.seconds)
        } else throw ScriptExitException("AP ran out!")
    }

    private fun needsToWithdraw(): Boolean {
        return Game.WithdrawRegion.exists(ImageLocator.Withdraw)
    }

    private fun withdraw() {
        if (!Preferences.WithdrawEnabled) {
            throw ScriptExitException("All servants have been defeated and auto-withdrawing is disabled.")
        }

        // Withdraw Region can vary depending on if you have Command Spells/Quartz
        val withdrawRegion = Game.WithdrawRegion
            .findAll(ImageLocator.Withdraw)
            .firstOrNull() ?: return

        withdrawRegion.Region.click()

        AutomataApi.wait(0.5.seconds)

        // Click the "Accept" button after choosing to withdraw
        Game.WithdrawAcceptClick.click()

        AutomataApi.wait(1.seconds)

        // Click the "Close" button after accepting the withdrawal
        Game.StaminaBronzeClick.click()
    }

    private fun needsToStorySkip(): Boolean {
        // TODO: Story Skip doesn't work correctly
        //if (Game.MenuStorySkipRegion.exists(ImageLocator.StorySkip))
        return Preferences.StorySkip
    }

    // Click begin quest in Formation selection, then select boost item, if applicable, then confirm selection.
    private fun startQuest() {
        Game.MenuStartQuestClick.click()

        AutomataApi.wait(2.seconds)

        val boostItem = Preferences.BoostItemSelectionMode
        if (boostItem >= 0) {
            Game.MenuBoostItemClickArray[boostItem].click()

            // in case you run out of items
            Game.MenuBoostItemSkipClick.click()
        }

        if (Preferences.StorySkip) {
            AutomataApi.wait(10.seconds)

            if (needsToStorySkip()) {
                Game.MenuStorySkipClick.click()
                AutomataApi.wait(0.5.seconds)
                Game.MenuStorySkipYesClick.click()
            }
        }
    }

    // Checking if in menu.png is on screen, indicating you are in the screen to choose your quest
    private fun isInMenu(): Boolean {
        return Game.MenuScreenRegion.exists(ImageLocator.Menu)
    }

    // Reset battle state, then click quest and refill stamina if needed.
    private fun menu() {
        battle.resetState()

        if (Preferences.Refill.enabled) {
            val refillRepetitions = Preferences.Refill.repetitions
            if (refillRepetitions > 0) {
                AutomataApi.toast("$stonesUsed refills used out of $refillRepetitions")
            }
        }

        // Click uppermost quest
        Game.MenuSelectQuestClick.click()

        afterSelectingQuest()
    }

    private fun afterSelectingQuest() {
        AutomataApi.wait(1.5.seconds)

        // Inventory full. Stop script.
        when (Preferences.GameServer) {
            // We only have images for JP and NA
            GameServerEnum.En, GameServerEnum.Jp -> {
                if (Game.InventoryFullRegion.exists(ImageLocator.InventoryFull)) {
                    throw ScriptExitException("Inventory Full")
                }
            }
        }

        // Auto refill
        while (Game.StaminaScreenRegion.exists(ImageLocator.Stamina)) {
            refillStamina()
        }
    }

    // Checking if Quest Completed screen is up, specifically if Bond point/reward is up.
    private fun isInResult(): Boolean {
        if (Game.ResultScreenRegion.exists(ImageLocator.Result)
            || Game.ResultBondRegion.exists(ImageLocator.Bond)
            // We're assuming CN and TW use the same Master/Mystic Code Level up image
            || Game.ResultMasterLvlUpRegion.exists(ImageLocator.MasterLvlUp)) {
            return true
        }

        val gameServer = Preferences.GameServer

        // We don't have TW images for these
        if (gameServer != GameServerEnum.Tw) {
            return Game.ResultMasterExpRegion.exists(ImageLocator.MasterExp)
                    || Game.ResultMatRewardsRegion.exists(ImageLocator.MatRewards)
        }

        // Not in any result screen
        return false
    }

    // Click through reward screen, continue if option presents itself, otherwise continue clicking through
    private fun result() {
        // Validator document https://github.com/29988122/Fate-Grand-Order_Lua/wiki/In-Game-Result-Screen-Flow for detail.
        Game.ResultNextClick.click(55)

        // Checking if there was a Bond CE reward
        if (Game.ResultCeRewardRegion.exists(ImageLocator.Bond10Reward)) {
            if (Preferences.StopAfterBond10) {
                throw ScriptExitException("Bond 10 CE GET!")
            }

            Game.ResultCeRewardCloseClick.click()

            // Still need to proceed through reward screen.
            Game.ResultNextClick.click(35)
        }

        AutomataApi.wait(5.seconds)

        // Friend request dialogue. Appears when non-friend support was selected this battle. Ofc it's defaulted not sending request.
        if (Game.ResultFriendRequestRegion.exists(ImageLocator.FriendRequest)) {
            Game.ResultFriendRequestRejectClick.click()
        }

        AutomataApi.wait(1.seconds)

        // Only for JP currently. Searches for the Continue option after select Free Quests
        if (Preferences.GameServer == GameServerEnum.Jp && Game.ContinueRegion.exists(
                ImageLocator.Confirm
            )
        ) {
            // Needed to show we don't need to enter the "StartQuest" function
            isContinuing = true

            // Pressing Continue option after completing a quest, reseting the state as would occur in "Menu" function
            Game.ContinueClick.click()
            battle.resetState()

            // If Stamina is empty, follow same protocol as is in "Menu" function Auto refill.
            afterSelectingQuest()

            return
        }

        // Post-battle story is sometimes there.
        if (Preferences.StorySkip) {
            if (Game.MenuStorySkipRegion.exists(ImageLocator.StorySkip)) {
                Game.MenuStorySkipClick.click()
                AutomataApi.wait(0.5.seconds)
                Game.MenuStorySkipYesClick.click()
            }
        }

        AutomataApi.wait(10.seconds)

        // Quest Completion reward. Exits the screen when it is presented.
        if (Game.ResultCeRewardRegion.exists(ImageLocator.Bond10Reward)) {
            Game.ResultCeRewardCloseClick.click()
            AutomataApi.wait(1.seconds)
            Game.ResultCeRewardCloseClick.click()
        }

        AutomataApi.wait(5.seconds)

        // 1st time quest reward screen, eg. Mana Prisms, Event CE, Materials, etc.
        if (Game.ResultQuestRewardRegion.exists(ImageLocator.QuestReward)) {
            AutomataApi.wait(1.seconds)
            Game.ResultNextClick.click()
        }
    }

    // Selections Support option
    private fun support() {
        // Friend selection
        val hasSelectedSupport = support.selectSupport(Preferences.Support.selectionMode)

        if (hasSelectedSupport && !isContinuing) {
            AutomataApi.wait(4.seconds)
            startQuest()

            // Wait timer till battle starts.
            // Uses less battery to wait than to search for images for a few seconds.
            // Adjust according to device.
            AutomataApi.wait(10.seconds)
        }
    }

    // Initialize Aspect Ratio adjustment for different sized screens,ask for input from user for Autoskill plus confirming Apple/Stone usage
    // Then initialize the AutoSkill, Battle, and Card modules in modules.
    private fun init() {
        initScaling()

        autoSkill.init(battle, card)
        battle.init(autoSkill, card)
        card.init(autoSkill, battle)

        support.init()
    }

    override fun script(): Nothing {
        init()

        // a map of validators and associated actions
        // if the validator function evaluates to true, the associated action function is called
        val screens = mapOf(
            { Game.needsToRetry() } to { Game.retry() },
            { battle.isIdle() } to { battle.performBattle() },
            { isInMenu() } to { menu() },
            { isInResult() } to { result() },
            { isInSupport() } to { support() },
            { needsToWithdraw() } to { withdraw() },
            { isGudaFinalRewardsScreen() } to { gudaFinalReward() }
        )

        // Loop through SCREENS until a Validator returns true
        while (true) {
            val actor = AutomataApi.useSameSnapIn {
                screens
                    .filter { (validator, _) -> validator() }
                    .map { (_, actor) -> actor }
                    .firstOrNull()
            }

            actor?.invoke()

            AutomataApi.wait(1.seconds)
        }
    }

    /**
     * Special result screen check for GudaGuda Final Honnouji.
     *
     * The check only runs if `GudaFinal` is activated in the preferences and if the GameServer is
     * set to Japanese.
     *
     * When this event comes to other regions, the GameServer condition needs to be extended.
     */
    private fun isGudaFinalRewardsScreen(): Boolean {
        if (!Preferences.GudaFinal || Preferences.GameServer != GameServerEnum.Jp)
            return false

        return Game.GudaFinalRewardsRegion.exists(ImageLocator.GudaFinalRewards)
    }

    private fun gudaFinalReward() = Game.GudaFinalRewardsRegion.click()
}