package tfsd;

/**
 * Created by bsuv on 5/21/14.
 */
public class ConsesusApp {

    public ConsesusApp(ConsensusAppSession main) {
        main.registerCallback(this);
        main.propose(999);
    }

    public void run(int v) {
        System.out.println("Value " + v + " was decided.");
    }
}
