package com.game.guild;

import java.util.List;

/**
 * Read-only projection returned by {@code GET /api/guild/{guildId}}.
 * Combines the {@link Guild} header with its full member list.
 */
public class GuildView {

    private final Guild            guild;
    private final List<GuildMember> members;

    public GuildView(Guild guild, List<GuildMember> members) {
        this.guild   = guild;
        this.members = members;
    }

    public Guild             getGuild()   { return guild; }
    public List<GuildMember> getMembers() { return members; }
}
