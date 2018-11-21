package ch.uzh.ifi.ce.cabne.execution.modifiedclasses;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.math3.random.SobolSequenceGenerator;

import ch.uzh.ifi.ce.cabne.randomsampling.RandomGenerator;

// VBT 7.Nov 2018: This class is used to change how random samples are generated. Don't remember exactly how.

public class HackyRandomGenerator implements RandomGenerator {
	int dimension, batchsize;
	SobolSequenceGenerator generator;
	List<double[]> cachedValues;
	int index = -1;
	

	public HackyRandomGenerator(int dimension) {
		this.dimension = dimension;
		batchsize = 10000;
		cachedValues = new ArrayList<>();
		generator = new SobolSequenceGenerator(dimension);
	}
	
	public HackyRandomGenerator(int dimension, int skip) {
		this(dimension);
		generator.skipTo(skip);
	}

	@Override
	public Iterator<double[]> nextVectorIterator() {
		
		Iterator<double[]> it = new Iterator<double[]>() {
			
            @Override
            public boolean hasNext() {
                return true;
            }

			@Override
			public double[] next() {
				index++;
				if (index >= cachedValues.size()) {
					moreSamples();
				}
				return cachedValues.get(index);
			}
		};
		return it;
	}
	
	public void advance() {
		// note that this is not thread-safe in any way. Also if iterators created before calling this are used after
		// calling this, weird things happen.
		// TODO: could add safety against this, e.g. by letting each batch of iterators have their own cache
		// cachedValues = new... etc, which deallocates old cache as soon as no references exist anymore
		cachedValues.clear();
	}
	
	public void resetIndex() {
		index = -1;
	}
	
	private void moreSamples() {
		for (int i=0; i<batchsize; i++) {
			cachedValues.add(generator.nextVector());
		}
	}
}
