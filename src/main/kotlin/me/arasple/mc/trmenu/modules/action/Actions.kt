package me.arasple.mc.trmenu.modules.action

import me.arasple.mc.trmenu.configuration.property.Nodes
import me.arasple.mc.trmenu.modules.action.base.Action
import me.arasple.mc.trmenu.modules.action.impl.*
import me.arasple.mc.trmenu.modules.action.impl.data.ActionDataDelete
import me.arasple.mc.trmenu.modules.action.impl.data.ActionDataSet
import me.arasple.mc.trmenu.modules.action.impl.data.ActionMetaRemove
import me.arasple.mc.trmenu.modules.action.impl.data.ActionMetaSet
import me.arasple.mc.trmenu.modules.action.impl.entity.ActionHologram
import me.arasple.mc.trmenu.modules.action.impl.hook.cronus.ActionCronusEffect
import me.arasple.mc.trmenu.modules.action.impl.hook.eco.ActionGiveMoney
import me.arasple.mc.trmenu.modules.action.impl.hook.eco.ActionSetMoney
import me.arasple.mc.trmenu.modules.action.impl.hook.eco.ActionTakeMoney
import me.arasple.mc.trmenu.modules.action.impl.hook.eco.ActionTransferPay
import me.arasple.mc.trmenu.modules.action.impl.hook.playerpoints.ActionGivePoints
import me.arasple.mc.trmenu.modules.action.impl.hook.playerpoints.ActionSetPoints
import me.arasple.mc.trmenu.modules.action.impl.hook.playerpoints.ActionTakePoints
import me.arasple.mc.trmenu.modules.action.impl.item.ActionEnchantItem
import me.arasple.mc.trmenu.modules.action.impl.item.ActionGiveItem
import me.arasple.mc.trmenu.modules.action.impl.item.ActionRepairItem
import me.arasple.mc.trmenu.modules.action.impl.item.ActionTakeItem
import me.arasple.mc.trmenu.modules.action.impl.menu.*
import me.arasple.mc.trmenu.modules.hook.HookCronus
import me.arasple.mc.trmenu.utils.Tasks
import me.arasple.mc.trmenu.utils.Utils
import org.bukkit.entity.Player

/**
 * @author Arasple
 * @date 2020/7/7 10:35
 */
object Actions {

    val optionsBound = "( )?(_\\|\\|_|&&&)( )?".toRegex()
    val cachedActions = mutableMapOf<String, List<Action>>()
    val registeredActions = mutableListOf(
        // hook
        ActionGiveMoney(),
        ActionGivePoints(),
        ActionSetMoney(),
        ActionTransferPay(),
        ActionSetPoints(),
        ActionTakeMoney(),
        ActionTakePoints(),
        ActionCronusEffect(),
        // item
        ActionEnchantItem(),
        ActionGiveItem(),
        ActionRepairItem(),
        ActionTakeItem(),
        // menu
        ActionClose(),
        ActionOpen(),
        ActionRefresh(),
        ActionSetArgs(),
        ActionSetPage(),
        ActionMetaSet(),
        ActionMetaRemove(),
        ActionDataSet(),
        ActionDataDelete(),
        ActionSetTitle(),
        ActionSilentClose(),
        // normal
        ActionChat(),
        ActionActionbar(),
        ActionCatcher(),
        ActionReInput(),
        ActionCommand(),
        ActionCommandConsole(),
        ActionCommandOp(),
        ActionConnect(),
        ActionDelay(),
        ActionJavaScript(),
        ActionParticle(),
        ActionReturn(),
        ActionSound(),
        ActionTell(),
        ActionTellraw(),
        ActionTitle(),
        ActionHologram()
    )

    @JvmStatic
    fun registerAction(action: Action) = registeredActions.add(action.newInstance())

    @JvmStatic
    fun runActions(player: Player, actions: List<Action>): Boolean {
        var delay = 0L
        actions.filter { it.evalChance(player) && it.evalCondition(player) }.forEach {
            when {
                it is ActionReturn -> return false
                it is ActionDelay -> delay += it.getDelay(player)
                delay > 0 -> Tasks.delay(delay, true) { it.run(player) }
                else -> Tasks.task(true) { it.run(player) }
            }
        }
        HookCronus.reset(player)
        return true
    }

    @JvmStatic
    fun runCachedAction(player: Player, action: String) = runActions(player, cachedAction(action))

    @JvmStatic
    fun cachedAction(action: String) = cachedActions.computeIfAbsent(action) { readAction(action) }

    @JvmStatic
    fun readActions(anys: List<Any>): List<Action> = mutableListOf<Action>().let { actions ->
        anys.forEach { if (it.toString().isNotEmpty()) actions.addAll(readAction(it)) }
        return@let actions
    }

    @JvmStatic
    fun readAction(any: Any?): List<Action> {
        any ?: return emptyList()

        val actions = mutableListOf<Action>()
        val sharedOptions = mutableMapOf<Nodes, String>()

        if (any is String) {
            any.split(optionsBound).forEach { it ->
                val name = it.replace("<.+>".toRegex(), "").split(':')[0]
                val content = it.removePrefix(name).removePrefix(":").removePrefix(" ")
                val action = registeredActions.firstOrNull { name.toLowerCase().matches(it.name) }?.newInstance() ?: ActionUnknow().also { it.setContent(any) }

                if (action is ActionCatcher) action.setContent(content)
                else if (content.isNotBlank()) {
                    val result = Nodes.read(
                        content,
                        Nodes.CHANCE,
                        Nodes.DELAY,
                        Nodes.PLAYERS,
                        Nodes.REQUIREMENT
                    )
                    action.setContent(result.first)
                    action.options = result.second.toMutableMap()
                }

                action.options.forEach { (option, value) -> sharedOptions[option] = value }
                actions.add(action)
            }
        } else if (any is LinkedHashMap<*, *>) {
            any.entries.firstOrNull()?.let { it ->
                val key = it.key.toString()
                val value = Utils.asSection(it.value) ?: return@let
                val action = registeredActions.firstOrNull { key.toLowerCase().matches(it.name) }?.newInstance() ?: ActionUnknow().also { it.setContent(key) }
                action.setContent(value)
                actions.add(action)
            }
        }
        actions.forEach {
            sharedOptions.forEach { (option, value) ->
                if (!it.options.containsKey(option)) it.options[option] = value
            }
        }
        return actions
    }

    @JvmStatic
    fun writeActions(actions: List<Action>): List<String> = mutableListOf<String>().let { list ->
        actions.forEach { list.add(it.toString()) }
        return@let list
    }

}
