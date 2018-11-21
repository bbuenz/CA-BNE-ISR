package ch.uzh.ifi.ce.cabne.execution;

import java.io.IOException;

public class LLGSbatchEntryPoint {

	public static void main(String[] args) throws InterruptedException, IOException {
		
		// If you want to run only a subset of the algorithms or mechanisms, the easiest way is to modify this file.
		// Reasoning about the indices directly and calling a subset of them is error-prone and clunky.
		
		String entryPoint = args[0];
		int index = Integer.parseInt(args[1]);
		String configfile = args[2];
		String outputfolder = args[3];
		
		// HACK: indexoffset is an optional extra arg used only to bypass slurm's limit of 1000 subjobs for each array job.
		int indexoffset = 0;
		
		// Build a data structure containing all valid input that should be mapped to indices
		// NOTE: to only use a subset of all available options (e.g. for algorithms or controlPointModifiers), 
		// use Arrays.copyOfRange(Object[] src, int from, int to)
		
		String[][] fields;

		String[] algorithms = {"1baseline", "2quasi", "3common", "4dampened", "5pattern", "6adaptive"};
		String[] controlPointModifiers = {"1", "2", "4", "8", "16", "32", "64", "128", "256", "512", "1024", "2048", "4096", "8192", "16384", "32768", "65536", "131072"};
		String[] rules = {"quadratic", "proportional", "proxy", "nearestbid"};
		String[] alphas = {"1.0", "2.0"};	
		String[] gammas = {"0.0", "0.5"};
		
		int nRuns = 50;
		String[] runIndices = new String[nRuns];
		for (int i=0; i<nRuns; i++) {
			runIndices[i] = Integer.toString(i);
		}
		

		// call the class that actually performs the experiment
		switch (entryPoint) {
		case "LLGExtrinsicBNEpass1":
			fields = new String[7][];
			fields[0] = rules;
			fields[1] = alphas;
			fields[2] = gammas;
			fields[3] = algorithms;
			fields[4] = runIndices;
			fields[5] = new String[]{configfile};
			fields[6] = new String[]{outputfolder};

			//fields[3] = Arrays.copyOfRange(algorithms, 1, 6);
			//fields[3] = new String[]{"2quasi", "2bquasinohack"};
			//fields[0] = new String[]{"proxy"};
			

			if (args.length >= 5) {
				indexoffset = Integer.parseInt(args[4]);
			}
			LLGExtrinsicBNEpass1.main(parseIndex(index + indexoffset, fields));
			break;
		case "LLGExtrinsicBNEpass2":
			String pass1folder = args[4];
			fields = new String[8][];
			fields[0] = rules;
			fields[1] = alphas;
			fields[2] = gammas;
			fields[3] = algorithms;
			fields[4] = runIndices;
			fields[5] = new String[]{configfile};
			fields[6] = new String[]{outputfolder};
			fields[7] = new String[]{pass1folder};

			//fields[3] = Arrays.copyOfRange(algorithms, 1, 6);
			//fields[3] = new String[]{"2quasi", "2bquasinohack"};
			//fields[0] = new String[]{"proxy"};

			if (args.length >= 6) {
				indexoffset = Integer.parseInt(args[5]);
			}
			LLGExtrinsicBNEpass2.main(parseIndex(index + indexoffset, fields));
			break;
		case "LLGIntrinsicBNE":
			fields = new String[8][];
			fields[0] = rules;
			fields[1] = alphas;
			fields[2] = gammas;
			fields[3] = new String[]{"1naive", "2naiveeveryfive", "3adaptive"};
			fields[4] = runIndices;
			fields[5] = new String[]{configfile};
			fields[6] = new String[]{outputfolder};
			fields[7] = new String[]{"2"}; // experimentId

			if (args.length >= 5) {
				indexoffset = Integer.parseInt(args[4]);
			}
			LLGIntrinsicBNE.main(parseIndex(index + indexoffset, fields));
			break;
		case "LLGSearchVariance":
			fields = new String[8][];
			fields[0] = rules;
			fields[1] = alphas;
			fields[2] = gammas;
			fields[3] = new String[]{"3adaptive"};
			fields[4] = runIndices;
			fields[5] = new String[]{configfile};
			fields[6] = new String[]{outputfolder};
			fields[7] = new String[]{"4b"}; // experimentId
			LLGIntrinsicBNE.main(parseIndex(index, fields));
			break;
		case "LLGVerificationVariance":
			fields = new String[8][];
			fields[0] = rules;
			fields[1] = alphas;
			fields[2] = gammas;
			fields[3] = new String[]{"3adaptive"};
			fields[4] = runIndices;
			fields[5] = new String[]{configfile};
			fields[6] = new String[]{outputfolder};
			fields[7] = new String[]{"4c"}; // experimentId
			LLGIntrinsicBNE.main(parseIndex(index, fields));
			break;
		case "LLGUpperLowerBounds":
			fields = new String[6][];
			fields[0] = rules;
			fields[1] = alphas;
			fields[2] = controlPointModifiers;
			fields[3] = new String[]{"Exact", "Heuristic"};
			fields[4] = new String[]{configfile};
			fields[5] = new String[]{outputfolder};
			LLGUpperLowerBounds.main(parseIndex(index, fields));
			break;
		default:
			throw new RuntimeException("Don't recognize entry point.");
		}
	}

	static String[] parseIndex(int index, String[][] fields) {
		// fields:	A 2d array specifying a set of allowed values for each of several fields.
		// index:	An integer specifying one combination of values.
		// Return a value for each field, such that it is guaranteed that each combination of fields occurs 
		// at exactly 1 index.
		
		int multiplier = 1;
		for (int i=0; i<fields.length; i++) {
			multiplier *= fields[i].length;
		}
		
		if (index < 0 || index >= multiplier) {
			throw new RuntimeException("Invalid index");
		}
		
		String[] result = new String[fields.length];
		for (int i=0; i<fields.length; i++) {
			multiplier /= fields[i].length;
			result[i] = fields[i][index/multiplier];
			index %= multiplier;
		}
		return result;
	}
	
}
