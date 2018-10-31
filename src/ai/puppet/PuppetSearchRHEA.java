package ai.puppet;

import ai.RandomBiasedAI;
import ai.abstraction.HeavyRush;
import ai.abstraction.LightRush;
import ai.abstraction.RangedRush;
import ai.abstraction.WorkerRush;
import ai.abstraction.pathfinding.AStarPathFinding;
import ai.core.AI;
import ai.core.ParameterSpecification;
import ai.evaluation.EvaluationFunction;
import ai.evaluation.SimpleEvaluationFunction;
import ai.evaluation.SimpleSqrtEvaluationFunction3;
import rts.GameState;
import rts.PlayerAction;
import rts.units.UnitTypeTable;

import java.util.*;

public class PuppetSearchRHEA extends PuppetBase {
    int DEBUG=0;
    int POPULATION_SIZE;  // Number of genomes in a population
    int GENOME_LENGTH;    // Number of moves in a sequence
    int NUM_TO_PROMOTE;   // Number of best genomes to promote to next generation
    float MUTATION_RATE;  // Probability of mutating a gene
    int TOURNAMENT_SIZE;  // Crossover tournament size

    //Genome population[];
    ArrayList<Genome> population;
    AI policy1, policy2;
    int player;
    GameState gs_orig;

    class Genome{
        Move moveList[];           // Sequence of moves (the genome)
        MoveGenerator nextMoves;   // Possible next choice points for this player
        float evalscore;           // Evaluation score on this genome after rollout

        public Genome(int player, GameState gs){
            evalscore = 0.0f;
            nextMoves = new MoveGenerator(script.getChoiceCombinations(player, gs),player);
            Move firstMove = nextMoves.next();

            //Random rand = new Random();
            moveList = new Move[GENOME_LENGTH];
            for (int i=0 ; i < GENOME_LENGTH ; i++){
                //moveList[i] = new Move(nextMoves.choices.get(rand.nextInt(nextMoves.choices.size())), player);
                // Use the first choice point in the script as a baseline strategy
                moveList[i] = firstMove;
            }
        }

        public Genome clone(){
            GameState gs_clone = gs_orig.clone();
            int player_a = player;
            Genome g = new Genome(player_a, gs_clone);
            g.moveList = this.moveList;
            g.nextMoves = this.nextMoves;
            g.evalscore = this.evalscore;

            return g;
        }

        void mutate(int player){
            // For each gene in the genome, there is a probability of MUTATION_RATE that it will mutate to a random choice point for each generation
            Random rand = new Random();
            for (int i=0 ; i<moveList.length ; i++){
                if (rand.nextFloat() < MUTATION_RATE) {
                    moveList[i] = new Move(nextMoves.choices.get(rand.nextInt(nextMoves.choices.size())), player);
                }
            }
        }
        void crossover(Genome g){
            // Take a genome and mutate it with another genome by randomly picking each gene from either genome with a 50/50 chance
            Random rand = new Random();
            for (int i=0 ; i<this.moveList.length ; i++){
                if (rand.nextInt(2)==1){
                    this.moveList[i] = g.moveList[i];
                }
            }
        }
    }

    public PuppetSearchRHEA(UnitTypeTable utt) {
        this(100, -1,
                -1, -1, 100,
                10, 3, 3, 0.2f, 3,
                new RandomBiasedAI(),
                new SingleChoiceConfigurableScript(new AStarPathFinding(),
                        new AI[]{
                                new LightRush(utt, new AStarPathFinding()),
                                new WorkerRush(utt, new AStarPathFinding()),
                                new RangedRush(utt, new AStarPathFinding()),
                                new HeavyRush(utt, new AStarPathFinding())
                        }),
                new SimpleEvaluationFunction());
    }


    public PuppetSearchRHEA(int max_time_per_frame, int max_playouts_per_frame,
                            int max_plan_time, int max_plan_playouts,
                            int eval_playout_time,
                            int population_size, int genome_length, int num_to_promote, float mutation_rate, int tournament_size,
                            AI policy, ConfigurableScript<?> script, EvaluationFunction evaluation) {
        super(max_time_per_frame,max_playouts_per_frame,
                max_plan_time, max_plan_playouts,eval_playout_time,
                script,evaluation);

        POPULATION_SIZE = population_size;
        GENOME_LENGTH = genome_length;
        NUM_TO_PROMOTE = num_to_promote;
        MUTATION_RATE = mutation_rate;
        TOURNAMENT_SIZE = tournament_size;

        population = new ArrayList<Genome>();
        this.policy1 = policy.clone();
        this.policy2 = policy.clone();
    }

    @Override
    public void reset() {
        super.reset();
        policy1.reset();
        policy2.reset();
        population.clear();
        clearStats();
    }

    @Override
    public String statisticsString() {
        return "Average Number of Leaves: "+
                (allSearches>0 ? allLeaves/allSearches:"-")+
                ", Average Time: "+
                (allSearches>0 ? allTime/allSearches:"-");
    }
    void clearStats(){
        allTime=allLeaves=0;
        allSearches=-1;
    }
    long allLeaves;
    long allTime;
    long allSearches;

    @Override
    public AI clone() {
        PuppetSearchRHEA clone = new PuppetSearchRHEA(TIME_BUDGET,ITERATIONS_BUDGET,
                PLAN_TIME, PLAN_PLAYOUTS, STEP_PLAYOUT_TIME,
                POPULATION_SIZE, GENOME_LENGTH, NUM_TO_PROMOTE, MUTATION_RATE, TOURNAMENT_SIZE,
                policy1.clone(),script.clone(), eval);
        clone.lastSearchFrame = lastSearchFrame;
        clone.lastSearchTime = lastSearchTime;
        clone.population = population;
        return clone;
    }

    @Override
    // NOT IN USE!!! This AI must be used with PuppetNoPlan as a wrapper
    public PlayerAction getAction(int player, GameState gs) throws Exception {
        return new PlayerAction();
    }

    public void initPopulation(int player, GameState gs){
        // Fill an empty population with genomes
        for (int i=0 ; i<POPULATION_SIZE; i++){
            population.add(i, new Genome(player, gs));
        }
    }

    public void mutatePopulation(int player){
        // Mutate all genomes in the population
        for (Genome g:population){
            g.mutate(player);
        }
    }

    public void selectOffspring() throws Exception{
        //
        // This method selects a number of best offsprings and populate the rest using crossover and tournament
        //

        // Roll out and evaluate final game state for all genome in the population
        for (Genome g:population){
            GameState gs_clone = gs_orig.clone();
            int player1 = player;
            int player2 = 1-player;

            // Simulate all moves in the genome
            for(Move m:g.moveList){
                ConfigurableScript<?> sc1=script.clone();
                sc1.reset();
                sc1.setChoices(m.choices);
                policy2.reset();

                simulate(gs_clone, sc1, policy2, player1, player2, 1);
            }

            // Roll out using default policy for both players for the remaining evaluation playout time
            int remainingPlayoutTime = STEP_PLAYOUT_TIME - GENOME_LENGTH;
            if (remainingPlayoutTime>0){
                policy1.reset();
                policy2.reset();
                simulate(gs_clone, policy1, policy2, player1, player2, remainingPlayoutTime);
            }
            // Get the evaluation score for this genome
            g.evalscore = eval.evaluate(player1, player2, gs_clone);
        }

        // Sort the population by evaluation score first
        Collections.sort(population, new Comparator<Genome>() {
            public int compare(Genome g1, Genome g2) {
                if (g1.evalscore == g2.evalscore)
                    return 0;
                return g1.evalscore > g2.evalscore ? -1 : 1;
            }
        });

        // Keep best genomes and populate the remainder using crossover
        for (int i=NUM_TO_PROMOTE; i<POPULATION_SIZE; i++){
            //
            // Perform crossover
            //
            ArrayList<Integer> crossoverParentIndex = new ArrayList<>();

            // Run tournament to find 2 parents:
            // Select a group of genomes at random, and get the best. Repeat for 2 times.
            for (int k=0 ; k<2 ; k++) {
                // Get a group genomes at random
                Random rand = new Random();
                ArrayList<Integer> selectedParentIndex = new ArrayList<>();
                while (selectedParentIndex.size() < TOURNAMENT_SIZE) {
                    int newIndex = rand.nextInt(POPULATION_SIZE);
                    if (!selectedParentIndex.contains(newIndex)) {
                        selectedParentIndex.add(newIndex);
                    }
                }

                // Find the genome with the best evaluation score
                float best_evalscore = 0.0f;
                int bestParentIndex = 0;
                for (int j = 0; j < selectedParentIndex.size(); j++) {
                    int parentIndex = selectedParentIndex.get(j);
                    float current_evalscore = population.get(parentIndex).evalscore;
                    if (current_evalscore > best_evalscore || j == 0) {
                        best_evalscore = current_evalscore;
                        bestParentIndex = parentIndex;
                    }
                }
                // Add to list of parents to be used for crossover
                crossoverParentIndex.add(bestParentIndex);
            }

            // Crossover the two parents to produce an offspring if the two parents are different
            // Otherwise, just add the parent to the next generation
            Genome offspring = population.get(crossoverParentIndex.get(0)).clone();
            if (crossoverParentIndex.get(0) != crossoverParentIndex.get(1)) {
                // Perform crossover with the 2 selected parents
                offspring.crossover(population.get(crossoverParentIndex.get(1)));

                // Evaluate the offspring if performed crossover
                GameState gs_clone = gs_orig.clone();
                int player1 = player;
                int player2 = 1-player;

                // Simulate all moves in the offspring
                for(Move m:offspring.moveList){
                    ConfigurableScript<?> sc1=script.clone();
                    sc1.reset();
                    sc1.setChoices(m.choices);
                    policy2.reset();
                    simulate(gs_clone, sc1, policy2, player1, player2, 1);
                }
                // Rollout using policy for both players for the remaining evaluation playout time
                int remainingPlayoutTime = STEP_PLAYOUT_TIME - GENOME_LENGTH;
                if (remainingPlayoutTime>0){
                    policy1.reset();
                    policy2.reset();
                    simulate(gs_clone, policy1, policy2, player1, player2, remainingPlayoutTime);
                }
                // Update the evaluation score
                offspring.evalscore = eval.evaluate(player1, player2, gs_clone);
            }
            population.set(i, offspring);
        }
    }

    @Override
    public
    void startNewComputation(int player_a, GameState gs){
        player = player_a;
        gs_orig = gs;
        population.clear();
        initPopulation(player, gs);

        lastSearchFrame=gs.getTime();
        lastSearchTime=System.currentTimeMillis();
        allLeaves+=totalLeaves;
        allTime+=totalTime;
        allSearches++;
        totalLeaves = 0;
        totalTime=0;
    }
    @Override
    public
    PlayerAction getBestActionSoFar() throws Exception{
        assert(!PLAN):"This method can only be called when not using s standing plan";
        script.setDefaultChoices();

        // Find best genome in population
        float best_evalscore = 0.0f;
        int best_genome_index = -1;
        for (int i=0 ; i< POPULATION_SIZE ; i++){
            float curret_evalscore = population.get(i).evalscore;
            if (curret_evalscore > best_evalscore || i == 0) {
                best_evalscore = curret_evalscore;
                best_genome_index = i;
            }
        }
        // Return the first choice point in the best genome as the next action
        if (best_genome_index != -1) script.setChoices(population.get(best_genome_index).moveList[0].choices);
        return script.getAction(player, gs_orig);

    }
    @Override
    public
    void computeDuringOneGameFrame() throws Exception{
        frameStartTime = System.currentTimeMillis();
        long prev=frameStartTime;
        frameLeaves=0;
        if (DEBUG>=2) System.out.println("Search...");

        boolean overtime;
        do{
            mutatePopulation(player);
            selectOffspring();

            long next=System.currentTimeMillis();
            totalTime+=next-prev;
            prev=next;
            frameTime=next-frameStartTime;

            // End early to prevent timeouts
            overtime = (frameTime>((int) (TIME_BUDGET*0.8)));
        }while(!overtime && !searchDone());
    }

    boolean searchDone(){
        return PLAN && planBudgetExpired();
    }


    @Override
    public String toString(){
        return getClass().getSimpleName() + "("+
                TIME_BUDGET + ", " + ITERATIONS_BUDGET + ", " +
                PLAN_TIME + ", " + PLAN_PLAYOUTS + ", " + STEP_PLAYOUT_TIME + ", " +
                policy1 + ", " + script + ", " + eval + ")";
    }


    @Override
    public List<ParameterSpecification> getParameters() {
        List<ParameterSpecification> parameters = new ArrayList<>();

        parameters.add(new ParameterSpecification("TimeBudget",int.class,100));
        parameters.add(new ParameterSpecification("IterationsBudget",int.class,-1));
        parameters.add(new ParameterSpecification("PlanTimeBudget",int.class,5000));
        parameters.add(new ParameterSpecification("PlanIterationsBudget",int.class,-1));


        parameters.add(new ParameterSpecification("StepPlayoutTime",int.class,100));
        parameters.add(new ParameterSpecification("EvalPlayoutTime",int.class,100));
        parameters.add(new ParameterSpecification("Policy",AI.class,policy1));
//        parameters.add(new ParameterSpecification("Script",ConfigurableScript.class, script));
        parameters.add(new ParameterSpecification("EvaluationFunction", EvaluationFunction.class, new SimpleSqrtEvaluationFunction3()));

        return parameters;
    }


    public int getStepPlayoutTime() {
        return STEP_PLAYOUT_TIME;
    }

    public void setStepPlayoutTime(int a_ib) {
        STEP_PLAYOUT_TIME = a_ib;
    }

    public AI getPolicy() {
        return policy1;
    }

    public void setPolicy(AI a) throws Exception {
        policy1 = (AI) a.clone();
        policy2 = (AI) a.clone();
    }

    public EvaluationFunction getEvaluationFunction() {
        return eval;
    }

    public void setEvaluationFunction(EvaluationFunction a_ef) {
        eval = a_ef;
    }
}