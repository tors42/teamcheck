package teamcheck;

import module chariot;

import module java.base;

public record CheckUtil(
        String teamId,
        Consumer<Team> teamHandler,
        Consumer<User> userHandler,
        Predicate<User> userFilter) {

    public void process(Client client) {
        if (! (client.teams().byTeamId(teamId) instanceof Some(Team team))) {
            System.err.format("Couldn't find team [%s]%n", teamId);
            return;
        }

        teamHandler.accept(team);
        client.teams().usersByTeamIdFull(team.id()).stream()
            .map(TeamMemberFull::user)
            .filter(userFilter)
            .forEach(userHandler);
    }

    public static CheckUtil of(
            String teamId,
            Consumer<Team> teamHandler,
            Consumer<User> userHandler) {
        return of(teamId, teamHandler, userHandler, _ -> true);
    }

    static CheckUtil of(
            String teamId,
            Consumer<Team> teamHandler,
            Consumer<User> userHandler,
            Predicate<User> userFilter) {
        return new CheckUtil(teamId, teamHandler, userHandler, userFilter);
    }

    public interface Mate {
        boolean kick(String userId);
    }

    public Mate andMate(ClientAuth client, Runnable onTokenBeenRevoked) {
        return userToKick -> switch(client.teams().kickFromTeam(teamId, userToKick)) {
            case Ok() -> true;
            case Fail(_, String msg) -> {
                if (msg.contains("No such token"))
                    try { onTokenBeenRevoked.run(); } catch (Exception _) {}
                yield false;
            }
        };
    }
}
