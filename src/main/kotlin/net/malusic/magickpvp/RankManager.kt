package net.malusic.magickpvp

import net.luckperms.api.LuckPerms
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.model.user.User
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.InheritanceNode
import java.util.UUID

class RankManager(private val plugin: PluginManager) {

    private val luckPerms: LuckPerms = LuckPermsProvider.get()

    private var ranks: List<RankGroup> = emptyList()
    private var managedGroups: Set<String> = emptySet()

    val enabled: Boolean get() = ranks.isNotEmpty()

    fun load() {
        ranks = emptyList()
        managedGroups = emptySet()

        val settings = plugin.configuration.ranks
        if (!settings.enabled) return

        val track = luckPerms.trackManager.getTrack(settings.track)
        if (track == null) {
            plugin.logger.warning("ranks.track '${settings.track}' does not exist in LuckPerms, ranks are disabled.")
            return
        }

        val inTrack = track.groups.map { it.lowercase() }.toSet()
        val valid = settings.groups.filter { entry ->
            val ok = entry.group in inTrack
            if (!ok) plugin.logger.warning("Group '${entry.group}' is not in LuckPerms track '${settings.track}', ignoring it.")
            ok
        }
        if (valid.isEmpty()) {
            plugin.logger.warning("ranks.groups has no usable groups, ranks are disabled.")
            return
        }

        ranks = valid.sortedBy { it.xp }
        managedGroups = valid.map { it.group }.toSet()
    }

    fun rankFor(xp: Long): RankGroup? = ranks.lastOrNull { xp >= it.xp }

    fun nextRank(xp: Long): RankGroup? = ranks.firstOrNull { it.xp > xp }

    fun sync(uuid: UUID, xp: Long) {
        if (!enabled) return

        val target = rankFor(xp)?.group
        val others = if (target == null) managedGroups else managedGroups - target

        val loaded = luckPerms.userManager.getUser(uuid)
        if (loaded != null && alreadyCorrect(loaded, target, others)) return

        luckPerms.userManager.modifyUser(uuid) { user ->
            user.data().clear(NodeType.INHERITANCE.predicate { it.groupName in others })
            if (target != null) user.data().add(InheritanceNode.builder(target).build())
        }
    }

    private fun alreadyCorrect(user: User, target: String?, others: Set<String>): Boolean {
        val groups = user.getNodes(NodeType.INHERITANCE).map { it.groupName }
        return (target == null || target in groups) && groups.none { it in others }
    }
}
