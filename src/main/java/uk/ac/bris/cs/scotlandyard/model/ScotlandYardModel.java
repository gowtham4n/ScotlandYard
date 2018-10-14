package uk.ac.bris.cs.scotlandyard.model;

import static java.util.Collections.unmodifiableSet;
import static java.util.Objects.requireNonNull;
import static uk.ac.bris.cs.scotlandyard.model.Colour.BLACK;
import static uk.ac.bris.cs.scotlandyard.model.Ticket.*;
import java.util.*;
import java.util.function.Consumer;
import uk.ac.bris.cs.gamekit.graph.Edge;
import uk.ac.bris.cs.gamekit.graph.Graph;
import uk.ac.bris.cs.gamekit.graph.ImmutableGraph;

public class ScotlandYardModel implements ScotlandYardGame, Consumer<Move> {

	private List<Boolean> rounds;
	private Graph<Integer, Transport> graph;
	private ArrayList<ScotlandYardPlayer> playerList;
	private int indexOfCurrentPlayer = 0;
	private int currentRound = NOT_STARTED;
	private Set<Move> validMoves;
	private HashSet<Colour> winningPlayers = new HashSet<>();
	private ArrayList<Spectator> spectators = new ArrayList<>();
	private int mrXLastRevealLocation = 0;

	public ScotlandYardModel(List<Boolean> rounds, Graph<Integer, Transport> graph,
			PlayerConfiguration mrX, PlayerConfiguration firstDetective,
			PlayerConfiguration... restOfTheDetectives) {
            //null check
			this.rounds = requireNonNull(rounds);
            this.graph = requireNonNull(graph);
		    requireNonNull(mrX);
		    requireNonNull(firstDetective);
		    for(PlayerConfiguration restOfTheDetective : restOfTheDetectives){
		        requireNonNull(restOfTheDetective);
            }
			//check whether rounds and graph is empty
            if (rounds.isEmpty()) {
                throw new IllegalArgumentException("Empty rounds");
            }
            if (graph.isEmpty()){
		        throw new IllegalArgumentException("Empty graph");
            }
            //check mrX is black
            if (mrX.colour.isDetective()) {
                throw new IllegalArgumentException("MrX should be Black");
            }
            //check all players are validated
			//check players aren't null
            ArrayList<PlayerConfiguration> configurations = new ArrayList<>();
            for (PlayerConfiguration configuration : restOfTheDetectives)
                configurations.add(requireNonNull(configuration));
            configurations.add(0, firstDetective);
            configurations.add(0, mrX);
			//check players don't have same location
            Set<Integer> setl = new HashSet<>();
            for (PlayerConfiguration configuration : configurations) {
                if (setl.contains(configuration.location))
                    throw new IllegalArgumentException("Duplicate location");
                setl.add(configuration.location);
            }
			//check player don't have same colour
            Set<Colour> setc = new HashSet<>();
            for (PlayerConfiguration configuration : configurations) {
                if (setc.contains(configuration.colour))
                    throw new IllegalArgumentException("Duplicate colour");
                setc.add(configuration.colour);
            }
			//detectives should never have a double or secret ticket
			if(!(firstDetective.tickets.keySet().containsAll(Arrays.asList(Ticket.values()))) || (firstDetective.tickets.get(DOUBLE)>0) || (firstDetective.tickets.get(SECRET)>0)){
				throw new IllegalArgumentException("detectives should have double or secret");
			}
			for(PlayerConfiguration restOfTheDetective : restOfTheDetectives) {
            	if(!(restOfTheDetective.tickets.keySet().containsAll(Arrays.asList(Ticket.values()))) || (restOfTheDetective.tickets.get(DOUBLE)>0) || (restOfTheDetective.tickets.get(SECRET)>0)){
					throw new IllegalArgumentException("detectives should have double or secret");
				}
			}
            //check mrX isn't missing any tickets
			if(!(mrX.tickets.keySet().containsAll(Arrays.asList(Ticket.values())))){
				throw new IllegalArgumentException("mrX should have tickets");
			}
			//creating a list to hold Scotland Yard Players
			playerList = new ArrayList<>() ;
			playerList.add(new ScotlandYardPlayer(mrX.player ,mrX.colour ,mrX.location ,mrX.tickets));
			playerList.add(new ScotlandYardPlayer(firstDetective.player, firstDetective.colour, firstDetective.location,firstDetective.tickets));
			for(PlayerConfiguration d : restOfTheDetectives){
				playerList.add(new ScotlandYardPlayer(d.player, d.colour, d.location, d.tickets));
			}
	}

	@Override
	public void registerSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if(spectators.contains(spectator)){
			throw new IllegalArgumentException("Spectator already registered");
		}
		else{
			spectators.add(spectator);
		}
	}

	@Override
	public void unregisterSpectator(Spectator spectator) {
		requireNonNull(spectator);
		if(!(spectators.contains(spectator))){
			throw new IllegalArgumentException("Spectator wasn't registered");
		}
		else{
			spectators.remove(spectator);
		}
	}

	private Set<TicketMove> validTicketMoves(ScotlandYardPlayer player, int location){
		Set<TicketMove> validMoves = new HashSet<>();													//create a set of validMoves that holds TicketMove
		for (Edge<Integer, Transport> edge : getGraph().getEdgesFrom(getGraph().getNode(location))){	//take each edge from all edges around current location
			boolean occupied = false;																	//initially sets occupied to false
			for(ScotlandYardPlayer p : playerList){														//loops through all players
				if (p.isDetective() && (p.location() == edge.destination().value())){					//if the player is detective and its location is of the edge destination
					occupied = true;																	//set occupied to true
					break;																				//once location is found to be occupied, no need to continue to check if occupied
				}
			}
			if (occupied) continue;																		//if occupied, not a valid move, so skip
			Ticket ticket = fromTransport(edge.data());													//define what ticket is need to get traverse edge
			if (player.hasTickets(ticket)) {															//check is the player had that ticket
				validMoves.add(new TicketMove(player.colour(), ticket, edge.destination().value()));	//if player has ticket, add move to validMoves
			}
			if (player.hasTickets(SECRET)) {															//if player has secret ticket (only mrX)
				validMoves.add(new TicketMove(player.colour(), SECRET, edge.destination().value()));	//same move added, but with use of secret ticket
			}
		}
		return validMoves;
	}

	private Set<Move> validMoves(ScotlandYardPlayer player) {
		Set<TicketMove> validTicketMoves = validTicketMoves(player, player.location());							//creates a set of validTicketMoves that hold TicketMove for the player
		Set<Move> validMoves = new HashSet<>();																	//creates a set of ValidMoves that holds Moves
		validMoves.addAll(validTicketMoves);																	//add ValidTicketMoves to ValidMoves
		if(player.isMrX() && playerList.get(0).hasTickets(DOUBLE) && getCurrentRound() < (getRounds().size()-1)){	//if the player is mrX and he has double ticket and the round number allows for double ticket to be played
			for (TicketMove firstMove : validTicketMoves) {														//for each first move in the set of validTicketMoves
				for (TicketMove secondMove : validTicketMoves(player, firstMove.destination())) {				//for each second move in the set of newly generated validTicketmoves, from first move
					if(firstMove.ticket() == secondMove.ticket() && !player.hasTickets(firstMove.ticket(),2)) {	//if the first and second move are the same, ensure player has enough tickets
						continue;
					}
					validMoves.add(new DoubleMove(player.colour(), firstMove, secondMove));						//add DoubleMove from first and second move to set of validMoves
				}
			}
		}
		if (validMoves.isEmpty()) validMoves.add(new PassMove(player.colour()));						//if validMoves is empty add PassMove to ValidMoves
		return validMoves;
	}

	@Override
	public void startRotate() {
		if(!isGameOver()) {
			int x = indexOfCurrentPlayer;
			validMoves = validMoves(playerList.get(x));
			playerList.get(x).player().makeMove(this, playerList.get(x).location(), validMoves, this);
		}
		else throw new IllegalStateException("Cant' start rotate if game is already over");
	}

	private void spectatorDoubleMoveUpdating(Move move){
		for (Spectator s : getSpectators()) {
			s.onMoveMade(this, move);
		}
	}

	private void spectatorMrXMoveUpdating(Move move, int x){
		for (Spectator s : getSpectators()) {
			s.onRoundStarted(this, x);
		}
		for (Spectator s : getSpectators()) {
			s.onMoveMade(this, move);
		}
	}

	private void spectatorDetectiveUpdating(Move move){
		for (Spectator s : getSpectators()) {
			s.onMoveMade(this, move);
		}
	}

	@Override        																			//implementing Consumer<Move>
	public void accept(Move move){
		if (!validMoves.contains(requireNonNull(move))) {
			throw new IllegalArgumentException("valid moves contains moves not valid");
		}
		int previousPlayer = indexOfCurrentPlayer;
		if(indexOfCurrentPlayer < playerList.size()) {
			++indexOfCurrentPlayer;
		}
		if(indexOfCurrentPlayer == playerList.size()){
			indexOfCurrentPlayer = 0;
		}

		move.visit(new MoveVisitor() {	
			@Override
			public void visit(PassMove move) {
				if(previousPlayer == 0){
					currentRound++;
					ScotlandYardModel.this.spectatorMrXMoveUpdating(move, currentRound);
				}
				if(previousPlayer !=0) {
					ScotlandYardModel.this.spectatorDetectiveUpdating(move);
				}
			}
			@Override
			public void visit(TicketMove move) {
				ScotlandYardPlayer player = playerList.get(previousPlayer);
				player.location(move.destination());
				if(player.isMrX()){
					player.removeTicket(move.ticket());
					if(getRounds().get(getCurrentRound())){
						currentRound++;
						ScotlandYardModel.this.spectatorMrXMoveUpdating(move, currentRound);
						mrXLastRevealLocation = move.destination();
					}
					else{
						currentRound++;
						TicketMove masked = new TicketMove(move.colour(), move.ticket(), mrXLastRevealLocation);
						ScotlandYardModel.this.spectatorMrXMoveUpdating(masked, currentRound);
					}
				}
				if(player.isDetective()){
					reallocateTicket(move.ticket(), player);
					ScotlandYardModel.this.spectatorDetectiveUpdating(move);
				}
			}
			@Override
			public void visit(DoubleMove move) {
				if(getRounds().get(getCurrentRound()) && getRounds().get(getCurrentRound() + 1)){ 							//reveal reveal
					playerList.get(0).removeTicket(DOUBLE);
					DoubleMove doublemove = new DoubleMove(move.colour(), move.firstMove().ticket(), move.firstMove().destination(), move.secondMove().ticket(), move.secondMove().destination());
					ScotlandYardModel.this.spectatorDoubleMoveUpdating(doublemove);
				}
				if(getRounds().get(getCurrentRound()) && !getRounds().get(getCurrentRound() + 1)) {                       	//reveal hidden
					playerList.get(0).removeTicket(DOUBLE);
					DoubleMove doublemove = new DoubleMove(move.colour(), move.firstMove().ticket(), move.firstMove().destination(), move.secondMove().ticket(), move.firstMove().destination());
					ScotlandYardModel.this.spectatorDoubleMoveUpdating(doublemove);
				}
				if(!getRounds().get(getCurrentRound()) && getRounds().get(getCurrentRound() + 1)){							//hidden reveal
					playerList.get(0).removeTicket(DOUBLE);
					DoubleMove doublemove = new DoubleMove(move.colour(), move.firstMove().ticket(), mrXLastRevealLocation, move.secondMove().ticket(), move.secondMove().destination());
					ScotlandYardModel.this.spectatorDoubleMoveUpdating(doublemove);
				}
				if(!getRounds().get(getCurrentRound()) && !getRounds().get(getCurrentRound() +1)){							//hidden hidden
					playerList.get(0).removeTicket(DOUBLE);
					DoubleMove doublemove = new DoubleMove(move.colour(), move.firstMove().ticket(), mrXLastRevealLocation, move.secondMove().ticket(), mrXLastRevealLocation);
					ScotlandYardModel.this.spectatorDoubleMoveUpdating(doublemove);
				}
				visit(move.firstMove());
				visit(move.secondMove());
			}
			private void reallocateTicket(Ticket ticket, ScotlandYardPlayer player){
				player.removeTicket(ticket);
				playerList.get(0).addTicket(ticket);
			}
		});

		if(indexOfCurrentPlayer == 0){
			if(isGameOver()){
				for(Spectator s : getSpectators()){
					s.onGameOver(this, getWinningPlayers());
				}
			}
			else{
				for(Spectator s : getSpectators()){
					s.onRotationComplete(this);
				}
			}
		}
		else{
			validMoves = validMoves(playerList.get(indexOfCurrentPlayer));
			playerList.get(indexOfCurrentPlayer).player().makeMove(this, playerList.get(indexOfCurrentPlayer).location(), validMoves,this);
		}
	}

	@Override
	public Collection<Spectator> getSpectators() {
		return Collections.unmodifiableList(spectators);
	}

	@Override
	public List<Colour> getPlayers() {
		//converts list of ScotlandYardPlayers into a list of colours
		ArrayList<Colour> colourList = new ArrayList<>();
		for(ScotlandYardPlayer p: playerList){
			colourList.add(p.colour());
		}
		//return an immutable list of player colours
		return Collections.unmodifiableList(colourList);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	private boolean isPlayerStuck(ScotlandYardPlayer player) {
		for (Move move : validMoves(player)) {
			if(!(move instanceof PassMove)){
				return false;
			}
		}
		return true;
	}

	private boolean allDetectivesStuck(){
		for(ScotlandYardPlayer p : playerList){
			if(p.isDetective() && !(isPlayerStuck(p))){
				return false;
			}
		}
		return true;
	}

	private boolean isMrXCornered(){
		return((indexOfCurrentPlayer == 0) && isPlayerStuck(playerList.get(0)) && !(playerList.get(0).tickets().isEmpty()));
	}

	private boolean isMrXCaptured() {
		int mrXLocation = playerList.get(0).location();
		for(ScotlandYardPlayer p : playerList){
			if(p.isDetective() && p.location()== mrXLocation){
				return true;
			}
		}
		return false;
	}

	private boolean mrXEscaped(){
		return(getRounds().size() == getCurrentRound());
	}

	@Override
	public Set<Colour> getWinningPlayers() {
		if (winningPlayers.isEmpty()) {										//only checks for winning players, while set of winning players is empty
			if (mrXEscaped() || allDetectivesStuck()) {						//if mrX winning conditions
				winningPlayers.add(BLACK);									//add mrX colour to set of winning players
			}
			if (isMrXCaptured()|| isMrXCornered()) 							//if detectives winning conditions
				for (ScotlandYardPlayer p : playerList) {					//loop through players
					if (p.isDetective()) {									//check player is detective
						winningPlayers.add(p.colour());						// add detective colours to set of winning players
					}
				}
			}
		return unmodifiableSet(winningPlayers);
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public Optional<Integer> getPlayerLocation(Colour colour) {
		for (ScotlandYardPlayer p : playerList) {
			if (p.colour() == colour && p.isMrX()) {
				return Optional.of(mrXLastRevealLocation);
			}
			if (p.colour() == colour && p.isDetective()) {
				return Optional.of(p.location());
			}
		}
		return Optional.empty();
	}

	@Override
	public Optional<Integer> getPlayerTickets(Colour colour, Ticket ticket) {
		for (ScotlandYardPlayer p : playerList) {
			if (p.colour() == colour) {
				return Optional.of(p.tickets().get(ticket));
			}
		}
		return Optional.empty();
	}

	@Override
	public boolean isGameOver() {
		//game is over when the set of winning players is no longer empty
		return !getWinningPlayers().isEmpty();
	}

	@Override
	public Colour getCurrentPlayer() {
		return playerList.get(indexOfCurrentPlayer).colour();
	}

	@Override
	public int getCurrentRound() {
		return currentRound;
	}

	@Override
	public List<Boolean> getRounds() {
		return Collections.unmodifiableList(rounds);
	}

	@Override
	public Graph<Integer, Transport> getGraph() {
		return new ImmutableGraph<Integer, Transport>(graph);
	}
}
