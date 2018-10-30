package ai.strategytactics;

import ai.RandomBiasedAI;
import ai.abstraction.*;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.InterruptibleAI;
import ai.core.ParameterSpecification;
import ai.evaluation.SimpleEvaluationFunction;
import ai.mcts.naivemcts.NaiveMCTS;
import ai.puppet.*;
import rts.*;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

public class BillyPuppet extends AIWithComputationBudget implements InterruptibleAI {

    PuppetNoPlan strategy;
    NaiveMCTS tactic;
    int player;
    int weightStrategy, weightTactic;
    int timeBudgetCopy, iterationBudgetCopy;
    GameState _gs;
    boolean useProportionalBudget;

    public BillyPuppet(UnitTypeTable utt) {
        this(
                100, -1, false, 80, 20,
                new PuppetNoPlan(new PuppetSearchAB(
                        100, -1, 100, -1, 150,
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

    public BillyPuppet(int timeBudget, int iterationsBudget, boolean useProportionalBudget, int weightStrategy, int weightTactic, PuppetNoPlan strategy, NaiveMCTS tactics) {
        super(timeBudget, iterationsBudget);
        this.timeBudgetCopy = timeBudget;
        this.useProportionalBudget = useProportionalBudget;
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
                    this.useProportionalBudget,
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

        boolean haveOpponents = false;
        int reducedUnits = 0;
        int totalUnits = 0;


        for (Unit unitsInReducedGS: rgs.getUnits()){
            if (unitsInReducedGS.getPlayer() == 0 || unitsInReducedGS.getPlayer() ==1 )
            {haveOpponents = true;}
            if (unitsInReducedGS.getPlayer() != -1){
                reducedUnits = reducedUnits + 1;
            }
        }
        for (Unit unitsInGS: _gs.getUnits()){
            if (unitsInGS.getPlayer() != -1)
                totalUnits = totalUnits + 1;
        }

        if (!haveOpponents || !rgs.canExecuteAnyAction(player)) {
            strategy.setTimeBudget(TIME_BUDGET);
            strategy.startNewComputation(player, _gs);
            tactic.setTimeBudget(0);
        }
        else{
            //float strategyRatio = (float) weightStrategy/(float)(weightStrategy+weightTactic);
            //float tacticsRatio = (float) weightTactic/(float)(weightStrategy+weightTactic);
            int strategyBuget = TIME_BUDGET*weightStrategy/(weightStrategy+weightTactic);
            int tacticsBudget =  TIME_BUDGET*weightTactic/(weightStrategy+weightTactic);
            if (useProportionalBudget) {
                strategyBuget = TIME_BUDGET * reducedUnits/totalUnits;
                tacticsBudget = TIME_BUDGET - strategyBuget;
            }

            System.out.println("Strategy Ratio: " + strategyBuget + "  Tactic Ratio: " + tacticsBudget);
            strategy.setTimeBudget( strategyBuget);
            strategy.startNewComputation(player, _gs);
            tactic.setTimeBudget(tacticsBudget);
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
                List<Pair<Unit, UnitAction>> nonAttackActions = new ArrayList<Pair<Unit, UnitAction>>();
                for (Pair<Unit, UnitAction> ua : tacticAction.getActions())
                {
                    if (!ua.m_a.getType().canAttack ||
                    ua.m_b.getType() == UnitAction.TYPE_HARVEST ||
                    ua.m_b.getType() == UnitAction.TYPE_RETURN ||
                    ua.m_b.getType() == UnitAction.TYPE_PRODUCE){
                        nonAttackActions.add(ua);
                    }
                }

                for (Pair<Unit, UnitAction> ua : nonAttackActions)
                {
                    tacticAction.getActions().remove(ua);
                }

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

    public AI preGameAnalysis(UnitTypeTable utt){
        int aiChoice = 0;
        AI aiWillbeUsed = null;

        if (aiChoice == 0) {
            aiWillbeUsed = new PuppetNoPlan(new PuppetSearchAB(
                    100, -1, 100, -1, 100,
                    new SingleChoiceConfigurableScript(new AStarPathFinding(),
                            new AI[]{
                                    new WorkerRush(utt, new AStarPathFinding()),
                                    new LightRush(utt, new AStarPathFinding()),
                                    new RangedRush(utt, new AStarPathFinding()),
                                    new HeavyRush(utt, new AStarPathFinding())
                            }), new SimpleEvaluationFunction()));
        }
        else
        {
            aiWillbeUsed = new PuppetNoPlan(
                    new PuppetSearchMCTS(100, -1,
                            5000, -1,
                            100, 100,
                            new RandomBiasedAI(),
                            new BasicConfigurableScript(utt, new AStarPathFinding()),
                            new SimpleEvaluationFunction()));
        }


        return aiWillbeUsed;
    }

}
