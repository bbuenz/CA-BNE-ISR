package ch.uzh.ifi.ce.cabne.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.TreeMap;

import ch.uzh.ifi.ce.cabne.algorithm.BNESolverContext;
import ch.uzh.ifi.ce.cabne.domains.Mechanism;
import ch.uzh.ifi.ce.cabne.domains.LLG.LLGSampler;
import ch.uzh.ifi.ce.cabne.domains.LLG.Proxy;
import ch.uzh.ifi.ce.cabne.integration.MCIntegrator;
import ch.uzh.ifi.ce.cabne.strategy.Strategy;
import ch.uzh.ifi.ce.cabne.strategy.UnivariatePWLStrategy;


public class LLGUtilityLandscape extends LLGExperimentBase {
	
	
	public static void main(String[] args) throws InterruptedException, IOException {

		
		// some hardcoded configurations
		double alpha = 1.0;
		double gamma = 0.0;
		
		int nPlotPoints = 1000; // number of points used for plotting the utility landscape
		int gridSizeDiscrete = 100; // number of points used in discretized PWC strategy
		boolean globalIsPWC = true;
		
		// TODO: add config file
		String configfile = "/home/vbt/repositories/CA-BNE/config/LLG.config";
		
		Path outputFile = Paths.get("/home/vbt/Desktop/tmp/test.txt");

		String mechanismName = "proxy";
		Mechanism<Double, Double> mechanism = new Proxy();
		
		// create context
		BNESolverContext<Double, Double> context = new BNESolverContext<>();
		context.parseConfig(configfile);
		context.activateConfig("verificationstep");
		context.setMechanism(mechanism);
		context.setSampler(new LLGSampler(context));
		context.setIntegrator(new MCIntegrator<Double, Double>(context));

		// read general algorithm settings from config
		int gridSize = Integer.parseInt(context.config.get("gridsize"));
		
		
		configureAlgorithm("5pattern", context, 0);
		
		// create strategy profile that is discretized analytical BNE
		TreeMap<Double, Double> dataPWC = new TreeMap<>();
		dataPWC.put(0.0, 0.0);
		double analyticalOld = 0.0;
		for (int x=1; x<=gridSizeDiscrete; x++) {
			double v = ((double) x) / (gridSizeDiscrete);
			double analytical = analyticalBNE(v, mechanismName, alpha, gamma);
			dataPWC.put(v - 1e-9, analyticalOld);
			dataPWC.put(v, analytical);
			analyticalOld = analytical;
		}
		TreeMap<Double, Double> dataPWCGlobal = new TreeMap<>();
		dataPWCGlobal.put(0.0, 0.0);
		double truthfulOld = 0.0;
		for (int x=1; x<=2*gridSizeDiscrete; x++) {
			double v = ((double) x) / (gridSizeDiscrete);
			if (globalIsPWC) dataPWCGlobal.put(v - 1e-9, truthfulOld);
			dataPWCGlobal.put(v, v);
			truthfulOld = v;
			System.out.println(v);
		}
		Strategy<Double, Double> stratPWC = new UnivariatePWLStrategy(dataPWC); 
		ArrayList<Strategy<Double, Double>> stratsPWC = new ArrayList<>(3);
		stratsPWC.add(0, stratPWC);
		stratsPWC.add(1, stratPWC);
		stratsPWC.add(2, new UnivariatePWLStrategy(dataPWCGlobal));
		
		// create strategy profile that is exact analytical BNE
		TreeMap<Double, Double> dataSmooth = new TreeMap<>();
		for (int x=0; x<=gridSize; x++) {
			double v = ((double) x) / (gridSize);
			double analytical = analyticalBNE(v, mechanismName, alpha, gamma);
			dataSmooth.put(v, analytical);
		}
		Strategy<Double, Double> stratSmooth = new UnivariatePWLStrategy(dataSmooth);
		ArrayList<Strategy<Double, Double>> stratsSmooth = new ArrayList<>(3);
		stratsSmooth.add(0, stratSmooth);
		stratsSmooth.add(1, stratSmooth);
		stratsSmooth.add(2, UnivariatePWLStrategy.makeTruthful(0.0, 2.0));
		
		// -----------------------------------------------------------------------------------
		// finished setup, now comes the main loop
		// -----------------------------------------------------------------------------------
		
		// String builder that assembles the output.
		StringBuilder builder = new StringBuilder();
		
		int iteration = 0;
		//for (double v=0.0; v<=1.0; v+=0.25) {
		for (double v=0.6; v<=0.6; v+=0.25) {
			System.out.println("valuation " + v);
			context.advanceRngs();
			
			builder.append(String.format("%d 42", iteration++));
			
			for (int x=0; x<nPlotPoints; x++) {
				double bid = ((double) x) / nPlotPoints;
				double utilSmooth = context.integrator.computeExpectedUtility(0, v, bid, stratsSmooth);
				double utilPWC = context.integrator.computeExpectedUtility(0, v, bid, stratsPWC);
				
				// the next 2 lines are a hack to visualize the strategy profile itself
				//double utilSmooth = stratsPWC.get(0).getBid(bid);
				//double utilPWC = stratsPWC.get(2).getBid(bid);
				
				builder.append(String.format("  %5.4f %5.4f %5.4f", bid, utilSmooth, utilPWC));
			}
			if (v < 1.0) builder.append("\n");
		}

		System.out.println(builder.toString());
		
		Files.write(outputFile, builder.toString().getBytes(), 
				    StandardOpenOption.CREATE, 
				    StandardOpenOption.WRITE, 
				    StandardOpenOption.TRUNCATE_EXISTING);
	}

}
