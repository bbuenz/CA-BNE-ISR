package ch.uzh.ifi.ce.cabne;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.LLLLGG.LLLLGGBetaSampler;
import ch.uzh.ifi.ce.cabne.randomsampling.CommonRandomGenerator;
import ch.uzh.ifi.ce.cabne.randomsampling.NaiveRandomGenerator;
import org.junit.Test;

import java.util.Arrays;

public class SamplingTest {
    @Test
    public void testSampler() {
        BNESolverContext<Double[], Double[]> context = new BNESolverContext<>();

        context.setRng(12, new NaiveRandomGenerator(12));

        LLLLGGBetaSampler sampler = new LLLLGGBetaSampler(context, 1 / 3d, 1 / 3d);
        System.out.println(Arrays.deepToString(sampler.sampleGame()));
    }
    @Test
    public void incentiveSampler(){

    }
}
