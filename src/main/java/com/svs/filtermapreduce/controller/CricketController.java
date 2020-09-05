package com.svs.filtermapreduce.controller;

import com.svs.filtermapreduce.data.MatchRepository;
import com.svs.filtermapreduce.model.Match;
import com.svs.filtermapreduce.model.MatchSummary;
import com.svs.filtermapreduce.model.Record;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "api/matches")
public class CricketController {
    private MatchRepository repo = new MatchRepository();

    @GetMapping
    public List<Match> getMatches() {
        return repo.getMatches();
    }

    /**
     * Get matches for a specific team e.g. api/matches/New Zealand, api/matches/India?opponent=Pakistan
     *
     * @param team     the team
     * @param opponent the opponent
     * @return list of matches
     */
    @GetMapping(path = "/{team}")
    public ResponseEntity<List<Match>> getTeamMatches(@PathVariable(name = "team") String team,
                                                      @RequestParam(name = "opponent", required = false) String opponent) {
        // using streams to filter data down to just the teams wanted
        List<Match> matches = repo.getMatches().stream() // create a stream from the collection
                .filter(m -> {
                    return m.getInfo().getTeams().contains(team); // filter matches containing team given
                })
                .filter(m -> {
                    return opponent == null || m.getInfo().getTeams().contains(opponent); // return true if oppoent is null
                })
                .collect(Collectors.toList()); // collect all remaining elements in the pipeline to a list
        return ResponseEntity.ok() // use ResponseEntity to make it possible to add HTTP response header
                .header("count", String.format("%d", matches.size()))
                .body(matches);
    }

    /**
     * Get summary of given team by transforming the match objects into match summary objects.
     *
     * @param team     the given team
     * @param opponent the opponent
     * @return list of match summaries
     */
    @GetMapping(path = "/{team}/summaries")
    public ResponseEntity<List<MatchSummary>> getSummaries(@PathVariable(name = "team") String team,
                                                           @RequestParam(name = "opponent", required = false) String opponent) {

        List<MatchSummary> matches = repo.getMatches().stream()
                .filter(m -> m.getInfo().getTeams().contains(team)) // filter operation
                .filter(m -> opponent == null || m.getInfo().getTeams().contains(opponent)) // filter operation
                .peek((m) -> {
                    System.out.println(m.getInfo().getOutcome().getResult());
                })
                .map(m -> { // map operation
                    MatchSummary summary = new MatchSummary(m.getInfo().getDates().get(0),
                            m.getInfo().getCity(),
                            m.getInfo().getTeams(),
                            m.getInfo().getOutcome().getWinner());
                    return summary;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok()
                .header("count", String.format("%d", matches.size()))
                .body(matches);
    }

    /**
     * Returns a single item win-loss record reduced from all the elements in a stream for a given team.
     * @param team the team
     * @param opponent the opponent
     * @return the win-loss record
     */
    @GetMapping(path = "/{team}/record")
    public Record getWinLossRecord(@PathVariable(name = "team") String team,
                                   @RequestParam(name = "opponent", required = false) String opponent) {

        Record winLossRecord = repo.getMatches().stream()
                .filter(m -> m.getInfo().getTeams().contains(team))
                .filter(m -> opponent == null || m.getInfo().getTeams().contains(opponent))
                .filter(m -> !"no result".equalsIgnoreCase(m.getInfo().getOutcome().getResult())) // sometimes games get cancelled
                .reduce(new Record(team),   // this object will accumulate the wins and losses.
                        (r, m) -> {         // this BiFunction takes the accumulator and the current Match in the pipe
                            if (team.equalsIgnoreCase(m.getInfo().getOutcome().getWinner())) {
                                r.setWins(r.getWins() + 1);
                            } else if ("tie".equalsIgnoreCase(m.getInfo().getOutcome().getResult())) {
                                r.setTies(r.getTies() + 1);
                            } else {
                                r.setLosses(r.getLosses() + 1);
                            }
                            return r;
                        }, (a, b) -> {  // This is a combiner used if Parallel streams are used
                            return new Record(a.getTeam(),
                                    a.getWins() + b.getWins(),
                                    a.getLosses() + b.getLosses(),
                                    a.getTies() + b.getTies());
                        });

        return winLossRecord;
    }

    @PostMapping
    public void addMatch(@RequestBody Match match) {
        repo.getMatches().add(match);
    }
}
