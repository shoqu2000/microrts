
package ai.strategytactics;

import java.util.ArrayList;
import java.util.List;

import ai.RandomBiasedAI;
import ai.abstraction.HeavyRush;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.WorkerRush;
import ai.abstraction.partialobservability.POHeavyRush;
import ai.abstraction.partialobservability.POLightRush;
import ai.abstraction.partialobservability.PORangedRush;
import ai.abstraction.partialobservability.POWorkerRush;
import ai.abstraction.pathfinding.FloodFillPathFinding;
import ai.core.AI;
import ai.core.AIWithComputationBudget;
import ai.core.InterruptibleAI;
import ai.core.ParameterSpecification;
import ai.evaluation.SimpleEvaluationFunction;
import ai.evaluation.SimpleSqrtEvaluationFunction3;
import ai.mcts.naivemcts.NaiveMCTS;
import ai.puppet.PuppetNoPlan;
import ai.puppet.PuppetSearchAB;
import ai.puppet.SingleChoiceConfigurableScript;
import rts.GameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.ResourceUsage;
import rts.UnitAction;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import util.Pair;

public class StrategyTactics extends AIWithComputationBudget implements InterruptibleAI {

    PuppetNoPlan strategyAI;
    NaiveMCTS tacticsAI;
    protected int DEBUG=0;
    boolean flexibleBudget;
    int weightStrategy,weightTactics;
    int origTimeBudget,origItBudget;

    public StrategyTactics(UnitTypeTable utt)throws Exception{
        this(100,-1,false, 20,80,
                new PuppetNoPlan(
                        new PuppetSearchAB(
                                100, -1,
                                -1, -1,
                                100,
                                new SingleChoiceConfigurableScript(new FloodFillPathFinding(),
                                        new AI[]{
                                                new WorkerRush(utt, new FloodFillPathFinding()),
                                                new LightRush(utt, new FloodFillPathFinding()),
                                                new RangedRush(utt, new FloodFillPathFinding()),
                                                new HeavyRush(utt, new FloodFillPathFinding()),
                                        }),
                                new SimpleEvaluationFunction())
                )
                ,new NaiveMCTS(100,-1,100,10,
                        0.3f, 0.0f, 0.4f,
                        new RandomBiasedAI(),
                        new SimpleEvaluationFunction(), true));
    }

    public StrategyTactics(int mt, int mi,boolean flexibleBudget, int weightStrategy, int weightTactics, PuppetNoPlan strategyAI, NaiveMCTS tacticsAI) throws Exception{
        super(mt, mi);
        origTimeBudget=mt;
        origItBudget=mi;
        this.strategyAI=strategyAI;
        this.tacticsAI=tacticsAI;
        this.flexibleBudget = flexibleBudget;
//		this.strategyAI=new ContinuingAI(strategyAI);
//		this.tacticsAI=new ContinuingAI(tacticsAI);
        this.weightStrategy=weightStrategy;
        this.weightTactics=weightTactics;
    }


    @Override
    public void reset() {
        strategyAI.reset();
        tacticsAI.reset();
    }

    @Override
    public PlayerAction getAction(int player, GameState gs) throws Exception {
        //System.out.println("Warning: not using contnuingAI!!!");
        if (gs.canExecuteAnyAction(player)) {
            startNewComputation(player, gs.clone());
            computeDuringOneGameFrame();
            return getBestActionSoFar();
        } else {
            return new PlayerAction();
        }
    }


    @Override
    public AI clone() {
        try{
            return (AI)new StrategyTactics(
                    origTimeBudget,
                    origItBudget,
                    flexibleBudget,
                    weightStrategy,
                    weightTactics,
                    (PuppetNoPlan)strategyAI.clone(),
                    (NaiveMCTS)tacticsAI.clone());
        } catch(Exception e) {
            //TODO: fix this
            return null;
        }
    }

    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        //       parameters.add(new ParameterSpecification("TimeBudget",int.class,100));
        //      parameters.add(new ParameterSpecification("IterationsBudget",int.class,-1));
        //       parameters.add(new ParameterSpecification("StepPlayoutTime",int.class,100));
//        parameters.add(new ParameterSpecification("Script",ConfigurableScript.class, script));
//        parameters.add(new ParameterSpecification("TacticalEvaluationFunction", EvaluationFunction.class, new SimpleSqrtEvaluationFunction3()));
//       parameters.add(new ParameterSpecification("StrategicEvaluationFunction", EvaluationFunction.class, new SimpleSqrtEvaluationFunction3()));

        return parameters;
    }

    @Override
    public String toString(){
        return  getClass().getSimpleName() +
                "(" +strategyAI.getClass().getSimpleName()+
                ", " +tacticsAI.getClass().getSimpleName() + ")";
    }
    //ReducedGameState _rgs;
    GameState _gs;
    @Override
    public void startNewComputation(int player, GameState gs) throws Exception {
        if(DEBUG>=2)System.out.println("start");
        _gs=gs.clone();
        ReducedGameState rgs=new ReducedGameState(_gs);

        assert(_gs.getTime()==rgs.getTime());
        //if(DEBUG>=1)System.out.println("Frame: "+gs.getTime()+" original size: "+gs.getUnits().size()+", reduced size: "+rgs.getUnits().size());
        boolean p0=false,p1=false;
        for(Unit u:rgs.getUnits()){
            if(u.getPlayer()==0)p0=true;
            if(u.getPlayer()==1)p1=true;
            if(p0&&p1) break;
        }

        int reducedUnits = 0;
        int totalUnits = 0;

        for(Unit u:rgs.getUnits()){
            if (u.getPlayer() != -1){
                reducedUnits = reducedUnits + 1;
            }
        }

        for (Unit u: _gs.getUnits()){
            if (u.getPlayer() != -1)
                totalUnits = totalUnits + 1;
        }

        if(!(p0&&p1) || !rgs.canExecuteAnyAction(player)){
            if(DEBUG>=1)System.out.println("Strategy only");
            strategyAI.setTimeBudget(TIME_BUDGET);
            strategyAI.startNewComputation(player, _gs);
            tacticsAI.setTimeBudget(0);
        }else{
            int strategyBuget = TIME_BUDGET*weightStrategy/(weightStrategy+weightTactics);
            int tacticsBudget =  TIME_BUDGET*weightTactics/(weightStrategy+weightTactics);
            if (flexibleBudget) {
                strategyBuget = TIME_BUDGET * reducedUnits / totalUnits;
                tacticsBudget = TIME_BUDGET - strategyBuget;
            }
            strategyAI.setTimeBudget(strategyBuget);
            strategyAI.startNewComputation(player, _gs);

            tacticsAI.setTimeBudget(tacticsBudget);
            tacticsAI.startNewComputation(player, rgs);

            //assert(_gs.getTime()==rgs.getTime());
//			System.out.println(_gs);
//			System.out.println(rgs);
        }
    }

    @Override
    public void computeDuringOneGameFrame() throws Exception {
        if(DEBUG>=2)System.out.println("think strategy");
        strategyAI.computeDuringOneGameFrame();
        if(tacticsAI.getTimeBudget()>0){
            if(DEBUG>=2)System.out.println("think tactics");
            tacticsAI.computeDuringOneGameFrame();
        }
    }

    @Override
    public PlayerAction getBestActionSoFar() throws Exception {
        if(DEBUG>=2)System.out.println("get");
        if(tacticsAI.getTimeBudget()<=0){
            PlayerAction paStrategy=strategyAI.getBestActionSoFar();
            return paStrategy;
        }else{
            PlayerAction paStrategy=strategyAI.getBestActionSoFar();
            PlayerAction paTactics=tacticsAI.getBestActionSoFar();
            //System.out.println("Extra search with "+rgs.getUnits().size()+" units");
            if(DEBUG>=1)
                System.out.println("actions: "+paTactics.getActions());

            //remove non attacking units
            List<Pair<Unit,UnitAction>> toRemove=new ArrayList<Pair<Unit,UnitAction>>();
            for(Pair<Unit,UnitAction> ua:paTactics.getActions()) {
                if(!ua.m_a.getType().canAttack ||
                        ua.m_b.getType()==UnitAction.TYPE_PRODUCE ||
                        ua.m_b.getType()==UnitAction.TYPE_HARVEST ||
                        ua.m_b.getType()==UnitAction.TYPE_RETURN ){
                    toRemove.add(ua);
                    if(DEBUG>=1)
                        System.out.println("removed");
                }
            }
            for(Pair<Unit,UnitAction>ua:toRemove){
                //rgs.removeUnit(ua.m_a);
                paTactics.getActions().remove(ua);
            }


            PlayerAction paFull = new PlayerAction();
            //add extra actions
            List<Unit> skip=new ArrayList<Unit>();
            for(Pair<Unit,UnitAction> ua:paTactics.getActions()) {
                // check to see if the action is legal!
                PhysicalGameState pgs = _gs.getPhysicalGameState();
                ResourceUsage r = ua.m_b.resourceUsage(ua.m_a, pgs);
                boolean targetOccupied=false;
                for(int position:r.getPositionsUsed()) {
                    int y = position/pgs.getWidth();
                    int x = position%pgs.getWidth();
                    if (pgs.getTerrain(x, y) != PhysicalGameState.TERRAIN_NONE ||
                            pgs.getUnitAt(x, y) != null) {
                        targetOccupied=true;
                        break;
                    }
                }
                if(!targetOccupied && r.consistentWith(paStrategy.getResourceUsage(), _gs)){
                    paFull.addUnitAction(ua.m_a, ua.m_b);
                    paFull.getResourceUsage().merge(r);
                    if(DEBUG>=1)
                        System.out.println("Frame: "+_gs.getTime()+", extra action: "+ua);
                    skip.add(ua.m_a);
                }
                else{
                    if(DEBUG>=1)
                        System.out.println("inconsistent");
                }
            }

            //add script actions
            for(Pair<Unit,UnitAction> ua:paStrategy.getActions()) {
                boolean found=false;
                for(Unit u:skip){
                    if(u.getID()==ua.m_a.getID()){
                        found=true;
                        break;
                    }
                }
                if(found){//skip units that were assigned by the extra AI
                    if(DEBUG>=1)
                        System.out.println("skipping");
                    continue;
                }
                paFull.addUnitAction(ua.m_a, ua.m_b);
                paFull.getResourceUsage().merge(ua.m_b.resourceUsage(ua.m_a, _gs.getPhysicalGameState()));
            }
            return paFull;
        }
    }

}