import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Vector;

import javax.swing.JFrame;


public class Training {

	static int nFeat = HaarFeature.NO_FEATURES;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Vector<WeakClassifier> tr = Training.train();
		try {
			FileOutputStream saveFile = new FileOutputStream("trainingData.sav");
			ObjectOutputStream save = new ObjectOutputStream(saveFile);
			save.writeObject(tr);
			save.close();

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	static Vector train() {
		HaarFeature.init();
		int nIter = 200;

		double[][] fv_face = makeFeatureVector(
		        FileUtils.combinePath(EnvironmentConstants.PROJECT_ROOT, "TrainingImages", "FACES"),
				10000);
		int nFaces = fv_face.length;

		double[][] fv_Nface = makeFeatureVector(
		        FileUtils.combinePath(EnvironmentConstants.PROJECT_ROOT, "TrainingImages", "NFACES"),
				10000);
		int nNFaces = fv_Nface.length;

		double[] w_face = new double[nFaces];
		double[] w_Nface = new double[nNFaces];
		initWeights(w_face, nFaces, 1/(2*((double)nFaces)));
		initWeights(w_Nface, nNFaces, 1/(2*((double)nNFaces)));

		double[] mu_p = new double[nFeat];
		double[] mu_n = new double[nFeat];
		double[] thld = new double[nFeat];
		int[] p = new int[nFeat];
		double[] err_face   = new double[nFeat];
		double[] err_Nface  = new double[nFeat];
		double sum_p;
		double sum_n;
		TrainingResult tr;
		Vector<WeakClassifier> classifier = new Vector<>();
		JFrame frame = null;

		for (int n=0;n<nIter;n++) {

			long startTime = System.currentTimeMillis();

			sum_p = weightedMean(w_face, fv_face, nFaces, mu_p);
			sum_n = weightedMean(w_Nface, fv_Nface, nNFaces, mu_n);
			setThreshold(mu_p,mu_n,thld,p);

			setError(w_face, fv_face, nFaces,
					 thld, p, err_face, true);
			setError(w_Nface, fv_Nface, nNFaces,
					 thld, p, err_Nface, false);

			tr = getOptimal(w_face, err_face, sum_p,
					   		   w_Nface, err_Nface, sum_n);
			tr.thld = thld[tr.ind];
			tr.par  = p[tr.ind];
			
			updateWeights(w_face, fv_face, nFaces, tr, true);
			updateWeights(w_Nface, fv_Nface, nNFaces, tr, false);
			
			classifier.add(new WeakClassifier(tr.ind,tr.thld,tr.par,tr.alpha));


			if (frame != null) {
				frame.setVisible(false);
				frame.dispose();
			}
//			frame = HaarFeature.showFeatureImg(tr[n].ind);
			frame = HaarFeature.showClassifierImg(classifier);
			System.out.println("\nFeature no: "+n);
			System.out.println("True positives: "+testClassifier(fv_face, nFaces, classifier)*1000/nFaces+"%%");
			System.out.println("False positives: "+testClassifier(fv_Nface, nNFaces, classifier)*1000/nNFaces+"%%");
			System.out.println((System.currentTimeMillis()-startTime));
		}
		
		return classifier;
	}

	static int testClassifier(double[][] fv_face, int n, Vector<WeakClassifier> classifier) {

		int sumDetection = 0;
		for (int i=0;i<n;i++) {

			double sumH = 0;
			double sumA = 0;
			for(WeakClassifier c : classifier) {
				if(c.parity*fv_face[i][c.index] > c.parity*c.thld) {
					sumH += c.alpha;
				}
				sumA += c.alpha;
			}
			if(sumH>=sumA/2) {
				sumDetection++;
			}
		}
		return sumDetection;
	}

	/*
	 * Lowers the weight on correctly classified training data.
	 */
	static void updateWeights(double[] w, double[][] fv_face, int n,
			TrainingResult tr, boolean pos) {
		
		double beta = tr.err/(1-tr.err);
		tr.alpha = Math.log(1/beta);

		// Loop over files.
		for (int i=0;i<n;i++) {
			
			// True positive.
			if (pos && tr.par*fv_face[i][tr.ind]>tr.par*tr.thld) {
				w[i] *= beta;
			}
			
			// True negative.
			else if (!pos && tr.par*fv_face[i][tr.ind]<tr.par*tr.thld) {
				w[i] *= beta;
			}
		}
	}

	/*
	 * Sets all weights to some starting value.
	 */
	static void initWeights(double[] w, int n, double value) {
		for (int j=0;j<n;j++) {
			w[j] = value;
		}
	}

	/*
	 * Finds the weak classifier that minimizes the total error
	 * on the two training sets.
	 */
	static TrainingResult getOptimal(
			double[] w_face, double[] err_face, double sum_p,
			double[] w_Nface, double[] err_Nface, double sum_n) {

		TrainingResult tr = new TrainingResult();
		double minErr = Double.MAX_VALUE;
		double err;

		// Loop over features.
		for (int j=0;j<nFeat;j++) {
			
			// Find minimum total error.
			err = (err_face[j]+err_Nface[j]);
			if(err<minErr) {
				minErr = err;
				tr.ind = j;
			}
		}

		// The error should be computed with
		// normalized weights.
		tr.err = minErr/(sum_p+sum_n);
		return tr;
	}

	/*
	 *  Finds the weighted error for each weak classifier.
	 */
	static void setError(double[] w, double[][] fv_face, int n,
			double[] thld, int[] p, 
			double[] err, boolean pos) {

		// Start by zeroing all errors.
		for (int j=0;j<nFeat;j++) {
			err[j] = 0;
		}	
		
		// Loop over files.
		for(int i=0;i<n;i++)  {
			
			// Loop over features.
			for (int j=0;j<nFeat;j++) {
				
				// If a positive example fails detection.
				if (pos && p[j]*fv_face[i][j]<p[j]*thld[j]) {
					err[j] += w[i];
				}
				
				// If a negative example is detected.
				else if (!pos && p[j]*fv_face[i][j]>p[j]*thld[j]) {
					err[j] += w[i];
				}	
			}
		}	
	}

	/*
	 * Sets the array of thresholds thld such that 
	 * the threshold lies between the mean of the positive
	 * and negative examples.
	 */
	static void setThreshold(double[] mu_p, double[] mu_n,
			double[] thld, int[] p) {
		
		// Loop over all features.
		for (int j=0;j<nFeat;j++) {
			thld[j] = (mu_p[j]+mu_n[j])/2;
			
			// Heuristics (good) for setting
			// the parity.
			if(mu_p[j]>mu_n[j]) {
				p[j] = 1;
			}
			else {
				p[j] = -1;
			}
		}
	}

	/*
	 * Computes the mean along the first dimension
	 * of f, weighted by w. n is the length
	 * of the first dimension of f.
	 * The result is placed in mu.
	 * The function returns the sum of the 
	 * weights.
	 */
	static double weightedMean(double[] w, double[][] fv_face, int n, double[] mu) {

		// Set everything to zero
		double sum = 0;
		for (int j=0;j<nFeat;j++) {
			mu[j] = 0;
		}

		// Loop over files
		for (int i=0;i<n;i++) {

			// Loop over features
			for (int j=0;j<nFeat;j++) {
				mu[j] += w[i]*fv_face[i][j];
			}

			// Sum the weights
			sum += w[i];
		}

		// Normalization
		for (int j=0;j<nFeat;j++) {
			mu[j] /= sum;
		}

		return sum;
	}

	/*
	 * Returns an nFiles-by-nFeat array of features
	 * for at most nMax files in the specified dir.
	 */
	static double[][] makeFeatureVector(String dir, int nMax) {; 

        File folder = new File(dir);
        File[] listOfFiles = folder.listFiles();

        int nFiles = Math.min(listOfFiles.length, nMax);
        double[][] fv = new double[nFiles][nFeat];

        for(int fileNo = 0; fileNo < nFiles; fileNo++) {
            IntegralImage img = new IntegralImage(listOfFiles[fileNo]);
            HaarFeature fet = new HaarFeature(img);

            for(int ind = 0; ind < nFeat; ind++) {
                fv[fileNo][ind] = fet.computeFeature(ind);
            }
        }

        return fv;
	}

}
