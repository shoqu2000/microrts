package ai.strategytactics;

import ai.abstraction.HeavyRush;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.WorkerRush;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.InterruptibleAI;
import ai.core.ParameterSpecification;
import ai.evaluation.SimpleEvaluationFunction;
import ai.mcts.naivemcts.NaiveMCTS;
import ai.puppet.PuppetNoPlan;
import ai.puppet.PuppetSearchAB;
import ai.puppet.SingleChoiceConfigurableScript;
import rts.*;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import util.Pair;

import java.util.ArrayList;
import java.util.List;

public class BillyPuppet extends AIWithComputationBudget implements InterruptibleAI {

    PuppetNoPlan strategy;
    NaiveMCTS tactic;
    int player;
    int weightStrategy, weightTactic;
    int timeBudgetCopy, iterationBudgetCopy;
    GameState _gs;

    public BillyPuppet(UnitTypeTable utt) {
        this(
                100, -1, 80, 20,
                new PuppetNoPlan(new PuppetSearchAB(
                        100, -1, 100, -1, 100,
                        new SingleChoiceConfigurableScript(new AStarPathFinding(),
                                new AI[]{
                                        new WorkerRush(utt, new AStarPathFinding()),
                                        new LightRush(utt, new AStarPathFinding()),
                                        new RangedRush(utt, new AStarPathFinding()),
                                        new HeavyRush(utt, new AStarPathFinding())
                                }),
                        new SimpleEvaluationFunction())
                ),
                new NaiveMCTS(100, -1, 150, 5, 0.3f, 0.0f, 0.4f,
                        new WorkerRush(utt),
                        new SimpleEvaluationFunction(), true));
    }

    public BillyPuppet(int timeBudget, int iterationsBudget, int weightStrategy, int weightTactic, PuppetNoPlan strategy, NaiveMCTS tactics) {
        super(timeBudget, iterationsBudget);
        this.timeBudgetCopy = timeBudget;
        this.iterationBudgetCopy = iterationsBudget;
        this.weightStrategy = weightStrategy;
        this.weightTactic = weightTactic;
        this.strategy = strategy;
        this.tactic = tactics;
    }

    @Override
    public void reset() {
        this.strategy.reset();
        this.tactic.reset();
    }

    @Override
    public PlayerAction getAction(int player, GameState gs) throws Exception {
        if (gs.canExecuteAnyAction(player))
        {
            this.player = player;
            startNewComputation(player, gs.clone());
            computeDuringOneGameFrame();

            return getBestActionSoFar();
        }
        else
            return new PlayerAction();
    }

    @Override
    public AI clone() {
            return new BillyPuppet(
                    this.timeBudgetCopy,
                    this.iterationBudgetCopy,
                    this.weightStrategy,
                    this.weightTactic,
                    this.strategy, this.tactic);
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameter = new ArrayList<>();
        return parameter;
    }

    public void startNewComputation(int player, GameState gs) throws Exception {
        _gs = gs.clone();

        GameState rgs = new ReducedGameState(_gs);

        assert(_gs.getTime()==rgs.getTime());

        boolean haveOpponents = false;


        for (Unit unitsInReducedGS: rgs.getUnits()){
            if (unitsInReducedGS.getPlayer() == 0 || unitsInReducedGS.getPlayer() ==1 )
            {haveOpponents = true;}
            System.out.println("This unit is in RGS: " + unitsInReducedGS);
        }
        System.out.println("========================");


        if (!haveOpponents || !rgs.canExecuteAnyAction(player)) {
            strategy.setTimeBudget(TIME_BUDGET);
            strategy.startNewComputation(player, _gs);
            tactic.setTimeBudget(0);
        }
        else{
            //TODO The time Budget adjust
            strategy.setTimeBudget(TIME_BUDGET*weightStrategy/(weightStrategy+weightTactic));
            strategy.startNewComputation(player, _gs);
            tactic.setTimeBudget(TIME_BUDGET*weightTactic/(weightTactic+weightStrategy));
            tactic.startNewComputation(player, rgs);
        }

    }

    public void computeDuringOneGameFrame() throws Exception {
        strategy.computeDuringOneGameFrame();
        if (tactic.getTimeBudget() > 0)
            tactic.computeDuringOneGameFrame();
    }

    public PlayerAction getBestActionSoFar() throws Exception {
        PlayerAction mergedAction = new PlayerAction();
        List<Unit> unitsForTactic = new ArrayList<Unit>();

        if (tactic.getTimeBudget() < 1) {
            return  strategy.getBestActionSoFar();
        }
        else {
            PlayerAction strategyAction = strategy.getBestActionSoFar();
            PlayerAction tacticAction = tactic.getBestActionSoFar();



            for (Pair<Unit, UnitAction> actionPair : tacticAction.getActions()){
                boolean targetOccupied = false;
                if (actionPair.m_a.getPlayer() != player)
                    break;
                PhysicalGameState pgs = _gs.getPhysicalGameState();
                ResourceUsage ru = actionPair.m_b.resourceUsage(actionPair.m_a, pgs);
                for(int position: ru.getPositionsUsed()) {
                    int x = position % pgs.getWidth();
                    int y = position / pgs.getWidth();
                    if (pgs.getTerrain(x, y) != PhysicalGameState.TERRAIN_NONE || pgs.getUnitAt(x, y) != null) {
                        targetOccupied = true;
                        break;
                    }
                }
                List<Pair<Unit, UnitAction>> nonMilitaryUnit = new ArrayList<Pair<Unit, UnitAction>>();


                if (!targetOccupied && ru.consistentWith(strategyAction.getResourceUsage(), _gs)) {
                    mergedAction.addUnitAction(actionPair.m_a, actionPair.m_b);
                    mergedAction.getResourceUsage().merge(ru);
                    unitsForTactic.add(actionPair.m_a);
                    System.out.println("Military Unit: " + actionPair.m_a);
                }
                else
                {
                    System.out.println("Inconsistent");
                }

            }

            //System.out.println("Military: " + unitsForTactic);
            for (Pair<Unit, UnitAction> actionPair : strategyAction.getActions()) {
                boolean isTactic = false;
                for (Unit u : unitsForTactic) {
                    if (actionPair.m_a.getID() == u.getID()) {
                        isTactic = true;
                    }
                }
                if (isTactic)
                    break;
                mergedAction.addUnitAction(actionPair.m_a, actionPair.m_b);
                mergedAction.getResourceUsage().merge(actionPair.m_b.resourceUsage(actionPair.m_a, _gs.getPhysicalGameState()));

            }
        }
        System.out.println("merged Action:" + mergedAction);
        return mergedAction;
    }
}
