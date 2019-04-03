package ch.uzh.ifi.ce.cabne.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.LLLLGG.*;
import ch.uzh.ifi.ce.cabne.domains.Mechanism;
import ch.uzh.ifi.ce.cabne.helpers.UtilityHelpers;
import ch.uzh.ifi.ce.cabne.integration.MCIntegrator;
import ch.uzh.ifi.ce.cabne.pointwiseBR.MultivariateCrossPattern;
import ch.uzh.ifi.ce.cabne.pointwiseBR.Optimizer;
import ch.uzh.ifi.ce.cabne.pointwiseBR.Pattern;
import ch.uzh.ifi.ce.cabne.pointwiseBR.PatternSearch;
import ch.uzh.ifi.ce.cabne.pointwiseBR.TestingPatternSearch;
import ch.uzh.ifi.ce.cabne.pointwiseBR.updateRule.MultivariateDampenedUpdateRule;
import ch.uzh.ifi.ce.cabne.randomsampling.CommonRandomGenerator;
import ch.uzh.ifi.ce.cabne.randomsampling.QuasiRandomGenerator;
import ch.uzh.ifi.ce.cabne.strategy.GridStrategy2D;
import ch.uzh.ifi.ce.cabne.strategy.VerificationStrategy;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;


public class LLLLGGBestResponse {
	
	public static void main(int minIndex, int maxIndex, String configfile, String strategyFileName, String outputFolderName, String state) throws InterruptedException, IOException {		
		Path strategyPath = Paths.get(strategyFileName);
		
		long starttime = System.currentTimeMillis();

		// ----------------------------------------------------------------------
		// STEP 1: READ CONFIGURATION FILE AND CONFIGURE ALGORITHM, MECHANISM ETC
		// ----------------------------------------------------------------------
		
		// create context and read config
		BNESolverContext<Double[], Double[]> context = new BNESolverContext<>();
		context.parseConfig(configfile);
		context.activateConfig(state);
		
		double targetepsilon = context.getDoubleParameter("epsilon");
		int gridsize = context.getIntParameter("gridsize");
		LLLLGGMechanism mechanism = getMechanism(context);

		// create pattern
		Pattern<Double[]> pattern = new MultivariateCrossPattern(2);
		//Pattern<Double[]> pattern = new MultivariateGaussianPattern(2, patternSize);
		
		// compute rng offset (we use common random numbers, and we don't want them to be the same each iteration)
		// NOTE: basename is not provided by Path class. Workaround: Convert it into a File, where basename is implemented implicitly
		String basename = strategyPath.toFile().getName(); // e.g. "iter1.strats", "iter97.strats"
		String iteration = basename.substring(4, basename.length()-7);
		int rngOffset = Integer.parseInt(iteration) * context.getIntParameter("rngoffsetperiter");
		if (context.hasParameter("rngoffsetbase")) {
			rngOffset += context.getIntParameter("rngoffsetbase");
		}
		System.out.format("iteration %s, rng offset %d\n", iteration, rngOffset);
		
		// initialize all algorithm pieces
		context.setOptimizer(new PatternSearch<>(context, pattern));
		context.setIntegrator(new MCIntegrator<>(context));
		System.out.println(mechanism);
		context.setMechanism(mechanism);
		context.setRng(10, new CommonRandomGenerator(10, rngOffset));
		context.setSampler(new LLLLGGSampler(context));
		context.setUpdateRule(new MultivariateDampenedUpdateRule<>(0.2, 0.7, 0.5 / targetepsilon, true));
		
		// HACK: optionally add t-tests
		if (context.getBooleanParameter("hacks.usetesting")) {
			context.setOptimizer(new TestingPatternSearch<>(context, pattern));
		}
		if (context.getBooleanParameter("hacks.useQuasi")) {
			System.out.println("using quasi");
			context.setRng(10, new QuasiRandomGenerator(10));
		}
		
		// ------------------------------------------------
		// STEP 2: PARSE STRATEGIES FROM PREVIOUS ITERATION
		// ------------------------------------------------

		// For the verification step, we need to keep around the original PWL and PWC strategy, 
		// as done in BNEAlgorithm.java and ExactGrid2DVerifier.java. 
		List<Strategy<Double[], Double[]>> strats = null;
		List<Strategy<Double[], Double[]>> stratsConverted = null;
		
		// read starting strategies from file if not read yet
		LLLLGGStrategyParser parser = new LLLLGGStrategyParser();
		List<GridStrategy2D> stratsParsed = parser.parse(strategyPath);
		strats = new ArrayList<>();
		stratsConverted = new ArrayList<>();
		if (state.equalsIgnoreCase("verificationstep")) {
			// need to convert the parsed strategies from PWL to PWC
			for (int j=0; j<6; j++) {
				strats.add(stratsParsed.get(j));
				int g = j<=3 ? gridsize : 2*gridsize;
				stratsConverted.add(new VerificationStrategy(g, 0.6, stratsParsed.get(j)));
			}
		} else {
			for (GridStrategy2D s : parser.parse(strategyPath)) {
				strats.add(s);
				stratsConverted.add(s);
			}
		}
		
		
		// ---------------------------------------------------------------------------------------
		// STEP 3: FOR EACH INDEX, DETERMINE ACTIVE POINT OF THIS ARRAY JOB (BIDDER AND VALUATION)
		//         THEN COMPUTE THE BEST RESPONSE AND WRITE IT OUT 
		// ---------------------------------------------------------------------------------------

		for (int batch=minIndex; batch<maxIndex; batch++) {
			int index = batch;
			gridsize = context.getIntParameter("gridsize"); // HACK: reset gridsize because it's manipulated
			
			// The first n*n array jobs are for the local players, 
			// the next n(n+1)/2 for the global players (fewer because of symmetry)
			boolean isGlobal = index >= (gridsize*gridsize);
			System.out.format("state  %s   isGlobal %s  index %d\n", state, isGlobal, index);
			if (isGlobal) {
				index -= gridsize * gridsize;
			}
			
			// player is 0 or 4, depending if local or global
			int i = isGlobal ? 4 : 0;
			
			// for verification, the global player has double gridsize.
			// Note that this check is done after doing index %= (gridsize*gridsize);
			// There are n*n array jobs for the local player
			// There are 2n(2n+1)/2 = jobs for the global players
			if (isGlobal && state.equalsIgnoreCase("verificationstep")) {
				gridsize *= 2;
			}
			
			// gridX, gridY are integers in {0, ..., gridsize}
			// x,y are numbers in [0,1] for local, [0,2] for global (including boundaries)
			int gridX, gridY;
			if (!isGlobal) {
				gridX = index / gridsize;
				gridY = index % gridsize;
			} else {
				// generate gridpoints where gridX >= gridY
				// e.g. gridsize=3 --> (0,0), (1,0), (1,1), (2,0), (2,1), (2,2)
				gridX = 0;
				gridY = index;
				for (int x=1; x<=gridY; x++) {
					gridY -= x;
					gridX++;
				}
			}
			
			Double[] v;
			if (state.equalsIgnoreCase("verificationstep")) {
				// need to adjust position of grid points according to polynomial
				v = ((VerificationStrategy) stratsConverted.get(i)).getGridpoint(new Integer[]{gridX, gridY});
				
			} else {
				double x = ((double) gridX) / (gridsize-1) * (isGlobal ? 2 : 1);
				double y = ((double) gridY) / (gridsize-1) * (isGlobal ? 2 : 1);
				v = new Double[]{x, y};
			}
			
			Path outputPath = Paths.get(outputFolderName, String.format("%04d-%s.tmp", index, isGlobal? "G" : "L"));
			if (Files.exists(outputPath)) {
				continue;
			}
			
			// compute BR for either local or global player
			Double[] oldbid = strats.get(i).getBid(v);
			Optimizer.Result<Double[]> result = context.optimizer.findBR(i, v, oldbid, stratsConverted);
			double epsilonAbs = UtilityHelpers.absoluteLoss(result.oldutility, result.utility);
			Double[] newbid = context.updateRule.update(v, oldbid, result.bid, result.oldutility, result.utility);
			
			// write out bid, epsilon, runtime, etc
			long endtime = System.currentTimeMillis();
	        StringBuilder builder = new StringBuilder("{\n"); 
	        builder.append(String.format("  'player': %d,\n", i));
	        builder.append(String.format("  'value': (%7.6f, %7.6f),\n", v[0], v[1]));
	        builder.append(String.format("  'bid': (%7.6f, %7.6f),\n", newbid[0], newbid[1]));
	        builder.append(String.format("  'br_utility': %7.6f,\n", result.utility));
	        builder.append(String.format("  'utility_loss': %7.6f,\n", epsilonAbs));
	        builder.append(String.format("  'runtime_ms': %d,\n", endtime - starttime));
	        builder.append("}");
			Files.write(outputPath, builder.toString().getBytes(), 
					    StandardOpenOption.CREATE, 
					    StandardOpenOption.WRITE, 
					    StandardOpenOption.TRUNCATE_EXISTING);
			starttime = endtime;
		}
    }

	public static LLLLGGMechanism getMechanism(BNESolverContext<Double[], Double[]> context) {
		// create mechanism
		LLLLGGMechanism mechanism;
		if (context.getBooleanParameter("hacks.useccg")) {
			throw new RuntimeException();
			//mechanism = new LLLLGGQuadraticCCG();
		} else {
			mechanism = new LLLLGGQuadratic();
		}

		if (context.hasParameter("hacks.paymentrule")) {
			switch (context.getStringParameter("hacks.paymentrule")) {
			case "firstprice":
				mechanism = new LLLLGGPayAsBid();
				break;
			case "proportional":
				mechanism = new LLLLGGProportional();
				break;
			case "proxy":
				mechanism = new LLLLGGProxy();
				break;
			case "allroundergeneric":
				// HACK to help out Benedikt
				String ref = context.getStringParameter("hacks.paymentrule.ref");
				String weight = context.getStringParameter("hacks.paymentrule.weight");
				double amp = context.getDoubleParameter("hacks.paymentrule.amp");
				mechanism = new LLLLGGParametrizedCoreSelectingRule(ref, weight, amp);
			case "":
			case "quadratic":
				// already set payment rule (same as if this config was not present
				break;
			default:
				throw new RuntimeException("don't recognize payment rule");
			}
		}
		return mechanism;
	}
}
