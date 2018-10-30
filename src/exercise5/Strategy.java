package exercise5;

import ai.abstraction.pathfinding.PathFinding;
import rts.*;
import rts.units.Unit;
import rts.units.UnitTypeTable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static rts.UnitAction.*;

public class Strategy {
    private Unit activeWorker = null;
    private ArrayList<UnitAction> buildBarracksActions;
    private ArrayList<UnitAction> harvestActions;
    private boolean harvesting, buildingBarracks, attacking;

    public Strategy() {
        buildBarracksActions = new ArrayList<>();
        harvestActions = new ArrayList<>();
        harvesting = true;
    }

    PlayerAction execute(int player, GameState gs, UnitTypeTable utt, PathFinding pf) {
        //We have one global behaviour. Think of this method as a hub to decide between different
        // behaviours at given times under whatever circumstances. For now, we just do a simple behaviour:

        return simpleBehaviour(player, gs, utt, pf);
    }

    /**
     * Level 2 exercises.
     *
     * @param player Your player ID.
     * @param gs     The current game state.
     * @param utt    The Unit Type Table
     * @param pf     Path finding algorithm
     * @return PlayerAction containing unit assignments.
     */
    private PlayerAction simpleBehaviour(int player, GameState gs, UnitTypeTable utt, PathFinding pf) {

        // Create a new player action. All actions used to your units are assigned here.
        PlayerAction pa = new PlayerAction();

        // We can only schedule actions if there's something for us to execute. Otherwise, pass.
        if (gs.canExecuteAnyAction(player)) {
            // Find the currently reserved resources and add them to the new PlayerAction object
            PhysicalGameState pgs = gs.getPhysicalGameState();

            for (Unit u : gs.getUnits()) {
                UnitActionAssignment uaa = gs.getActionAssignment(u);
                ResourceUsage ru = null;
                if (uaa != null) {
                    ru = uaa.action.resourceUsage(u, pgs); // This picks the usage of resources of unit 'u'
                }
                pa.getResourceUsage().merge(ru);                     // This merges the resource usage into 'pa'
            }


            // We only have 1 worker to start with, find it and set this as active worker.
            if (activeWorker == null) {
                for (Unit u : gs.getUnits()) {
                    if (u.getPlayer() == player) {
                        if (u.getType().canHarvest) {
                            activeWorker = u;
                        }
                    }
                }
            }

            // This block deals with the behaviour (sequence of commands) in TODOs 1-4
            if (gs.getActionAssignment(activeWorker) == null) {

                //First we harvest.
                if (harvesting) {
                    boolean finished = handleHarvest(gs, pa);
                    if (finished) {
                        harvesting = false;
                        buildingBarracks = true;
                    }

                    //After we have completed harvesting, we build barracks.
                } else if (buildingBarracks) {
                    boolean barracksBuilt = handleBuildBarracks(player, gs, pa, utt, pf);
                    if (barracksBuilt) {
                        buildingBarracks = false;

                        //After we have started building the barracks, we use the forward model to see the future.
                        handleRollState(gs, pa, player);

                        attacking = true;
                    }

                    //We send the worker to attack after the barracks are built.
                } else if (attacking && gs.getUnits().contains(activeWorker)) {
                    handleAttack(gs, pa, player, pf);
                }
            }


            // General rules for other units.
            for (Unit u : gs.getUnits()) {

                //TODO 6a: Set actions for this unit only if: its controlling player is 'this', they have no action
                //   assigned and they are not the activeWorker

                if (u.getPlayer() == player && u.getUnitActions(gs).isEmpty() && u != activeWorker ) {

                    List<UnitAction> availableActions;

                    //TODO 6b: Get the list of available actions for this unit and assign it to availableActions
                    availableActions = null;

                    UnitAction nextAction = null;

                    if (u.getType().canHarvest) { // This means this unit is a worker
                        //TODO 6c: Assign a random action from the available ones to 'nextAction'
                        // nextAction = ...
                    }

                    boolean barracksExist = gs.getPhysicalGameState().getUnitAt(4, 4) != null;
                    //TODO 6d: Set the first available action for this unit only if the barracks exist and the unit is a building:
                    if (true /*  Contents from TODO 6d */) { // Buildings

                        //TODO 6e: Assign the first one from the available actions list to 'nextAction'
                        // nextAction = ...
                    }

                    checkResourcesAndAddAction(nextAction, u, pa, gs);

                }
            }
        }

        return pa;
    }

    /**
     * Handles a harvest to a specific location. It first adds the required sequence of actions to a list, to
     * then select which is the next action to execute in the game.
     *
     * @param gs Current game state
     * @param pa PlayerAction to assign player actions.
     * @return true if harvest finishes in this game cycle.
     */
    private boolean handleHarvest(GameState gs, PlayerAction pa) {

        //Determine the list of actions to execute the harvest.
        if (harvestActions.size() == 0) {
            // contain all actions required to harvest (on the worker's left), move closer to the base (2,2),
            // return the resource to the base and be back at the original location (1,1).
            harvestActions.add(new UnitAction(TYPE_HARVEST, DIRECTION_LEFT));
            harvestActions.add(new UnitAction(TYPE_MOVE, DIRECTION_RIGHT));
            harvestActions.add(new UnitAction(TYPE_RETURN, DIRECTION_DOWN));
            //harvestActions.add(new UnitAction(TYPE_MOVE, DIRECTION_LEFT));
            /// create and add more here
        }

        // If there are pending actions, we have to pick one for this game tick.
        if (harvestActions.size() != 0) {
            // We pick (and remove) the next action from the list of harvestActions...
            UnitAction nextAction = harvestActions.remove(0);
            // and assign the action to the unit checking resources
            checkResourcesAndAddAction(nextAction, activeWorker, pa, gs);
        }

        if (harvestActions.size() == 0)
            return true;
        else{
            return false;}
    }


    /**
     * Handles the building of a barracks in a specific location. It first adds the required sequence of actions
     * to a list, to then select which is the next action to execute in the game.
     *
     * @param gs Current game state
     * @param pa PlayerAction to assign player actions.
     * @return true if all actions have been issued.
     */
    private boolean handleBuildBarracks(int player, GameState gs, PlayerAction pa, UnitTypeTable utt, PathFinding pf) {
        ResourceUsage ru = null;
        PhysicalGameState pgs = gs.getPhysicalGameState();
        // Create hardcoded action sequence for the active worker to harvest 1 resource and build Barracks at (4,4).


        if (buildBarracksActions.size() == 0 && gs.free(4, 4)) {
            // moving to next to the desired location (4,4) and building a barracks there.

            buildBarracksActions.add(pf.findPathToAdjacentPosition(activeWorker, (5+4*pgs.getWidth()), gs, ru));
            System.out.println("Worker's Position: " + activeWorker.getPosition(pgs) + "  Position of B: " + (4+3*pgs.getWidth()));
            if (activeWorker.getPosition(pgs) == 4+3*pgs.getWidth()) {
                buildBarracksActions.add(new UnitAction(TYPE_PRODUCE, DIRECTION_DOWN, utt.getUnitType("Barracks")));
                // create and add actions here.
            }
        }

        // If there are pending actions, we have to pick one for this game tick.
        if (buildBarracksActions.size() != 0) {
            UnitAction nextAction = buildBarracksActions.remove(0);
            checkResourcesAndAddAction(nextAction, activeWorker, pa, gs);
        }

        if (buildBarracksActions.size() == 0 && pgs.getUnitAt(4,4) != null)
            {
                System.out.println("Barrack is ready!");
                return true;}
        else {
            return false;
        }
    }

    /**
     * Rolls the state forward from the current state until the barracks are complete.
     *
     * @param gs       The current game state
     * @param pa       The actions to be executed.
     * @param playerID This player ID.
     */
    private void handleRollState(GameState gs, PlayerAction pa, int playerID) {

        int numStepsForward = 200;

        //TODO 3a: Create a copy of the current game state.
        GameState _gs = gs.clone();

        //TODO 3b: Issue the actions from pa in the copy of gs.
        _gs.issue(pa);

        //TODO 3c: Advance the state numStepsForward ahead
        while (numStepsForward != 0)
        {
            _gs.cycle();
            numStepsForward = numStepsForward -1;
        }

        int barracksCountNow = 0;
        int barracksCountThen = 0;

        //TODO 3d: Use the above counters to count the number of barracks before and after rolling the state forward.

        //TODO 3e: Print the number of barracks to confirm it was built before and after. Include the game tick for both cases.

    }

    /**
     * This function sends the active worker to attack the closest opponent unit.
     *
     * @param gs       Current game state
     * @param pa       PlayerAction where actions are issued.
     * @param playerID The ID of this player.
     * @param pf       Pathfinding object to find shortest paths.
     */
    public void handleAttack(GameState gs, PlayerAction pa, int playerID, PathFinding pf) {

        Unit closestEnemy = null;
        int closestDistance = 0;

        // TODO 4a: It's time to attack the closest enemy to your active worker. Find the closest opponent unit among all the units
        // belonging to the other player. You can use the function 'manhattanDistance' defined below to compute the distance between
        // two units.

        // If we found an enemy, attack them...
        if (closestEnemy != null) {

            UnitAction nextAction = null;

            // Attack if in range

            if (true /* TODO 4b: change this to True if the opponent is in attack range of the active worker (hint: look for a function in Unit)  */) {

                // TODO 4c: assign an attack action, on the location of the enemy, to nextAction
                // nextAction = ...

            } else {

                // Flattened position of the agent (required for pathfinding).
                int opponentFlatPosition = flattenedPosition(gs, closestEnemy.getX(), closestEnemy.getY());

                // TODO 4d: If not in range, use pathfinding to move towards the unit. Assign to nextAction the move
                //  that findPathToPositionInRange (from Pathfinding) provides.

                //nextAction = pf.findPathToPositionInRange(/* ... */);
            }

            checkResourcesAndAddAction(nextAction, activeWorker, pa, gs);
        }
    }

    /**
     * Computes the Manhattan distance between two units.
     * @param a One unit.
     * @param b Another unit.
     * @return the Manhattan distance. As expected.
     */
    private int manhattanDistance(Unit a, Unit b)
    {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }

    /**
     * Returns the position (x,y) flattened. This is, the location of that cell in a vector instead of a matrix.
     * @param gs Game state, needed for the with of the board or map.
     * @param x x position
     * @param y y position
     * @return position in a vector.
     */
    private int flattenedPosition(GameState gs, int x, int y)
    {
        return x + y * gs.getPhysicalGameState().getWidth();
    }

    public Strategy clone() {
        Strategy myClone = new Strategy();
        myClone.activeWorker = activeWorker.clone();
        myClone.buildBarracksActions.addAll(buildBarracksActions);
        return myClone;
    }

    /**
     * Method to check resources needed are available before adding an action assignment to the PlayerAction
     * @param nextAction    The action to be added to PlayerAction
     * @param u             The unit the action is assigned to
     * @param pa            The PlayerAction object to receive new unit assignment
     * @param gs            The current game state
     */
    private void checkResourcesAndAddAction(UnitAction nextAction, Unit u, PlayerAction pa, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        //If there are enough resources and the action is allowed for this unit
        if (nextAction != null && nextAction.resourceUsage(u, pgs).consistentWith(pa.getResourceUsage(), gs) &&
                (gs.isUnitActionAllowed(u, nextAction)) ) {
            addActionWithResourceUsage(nextAction, u, pa, gs);
        }
    }

    /**
     * Method to add an action assignment and resource usage to the PlayerAction
     * @param nextAction    The action to be added to PlayerAction
     * @param u             The unit the action is assigned to
     * @param pa            The PlayerAction object to receive new unit assignment
     * @param gs            The current game state
     */
    private void addActionWithResourceUsage(UnitAction nextAction, Unit u, PlayerAction pa, GameState gs) {
        PhysicalGameState pgs = gs.getPhysicalGameState();
        ResourceUsage ru = nextAction.resourceUsage(u, pgs);
        pa.getResourceUsage().merge(ru);
        pa.addUnitAction(u, nextAction);
    }
}
