import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;


public class ImageScanner {

	HaarFeature f;
	CascadeClassifier classifier;
	double pixelSkip = 1.0;
	double scaleSkip = 1.10;
	double thld_gain = 1.3;
	double startScale = 3;

	public static void main(String[] args) {
		File folder = new File(FileUtils.combinePath(EnvironmentConstants.PROJECT_ROOT, "TestImages"));
		File[] listOfFiles = folder.listFiles();
		File file = listOfFiles[7];
		try {
			BufferedImage srcImage = ImageIO.read(file);

			IntegralImage img = IntegralImage.makeIntegralImage(file);
			ImageScanner imgScanner = new ImageScanner();
			imgScanner.f = new HaarFeature(img);

			FileInputStream saveFile = new FileInputStream("trainingData.sav");
			ObjectInputStream restore = new ObjectInputStream(saveFile);
			Object tr = restore.readObject();
			restore.close();

			HaarFeature.init();
			
			JFrame frame = new JFrame();
			ImageIcon icon = new ImageIcon();

			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			frame.getContentPane().setLayout(new FlowLayout());
			frame.getContentPane().add(new JLabel(icon));
			frame.pack();
			frame.setVisible(true);
			
			imgScanner.classifier = (CascadeClassifier) tr;
			
			long startTime = System.currentTimeMillis();
			List<Detection> list = imgScanner.scan();
			System.out.println((System.currentTimeMillis()-startTime));

			//			for (Detection c : list) {
			//				System.out.println("x: "+c.x+" y: "+c.y+" w: "+c.w);
			//			}
			System.out.println("Found: "+list.size());
			drawBoundingBoxes(srcImage, list, 1, icon);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	List<Detection> scan() {
		List<Detection> list = new LinkedList<Detection>();
		int nTests = 0;
		double scale = startScale;

		int w = (int) Math.round(((double)HaarFeature.MIN_PATCH_SIDE)*scale);
		while (w<=Math.min(f.img.height, f.img.width)) {
			int x=0;
			while (x<(f.img.width-w)) {
				int y=0;
				while (y<(f.img.height-w)) {
					if (testPatch(x,y,w)) {
						list.add(new Detection(x,y,w));
					}
					nTests++;
					y = (int) (y+Math.round(pixelSkip*scale));
				}
				x = (int) (x+Math.round(pixelSkip*scale));
			}
			scale *= scaleSkip;
			w = (int) Math.round(((double)HaarFeature.MIN_PATCH_SIDE)*scale);
		}		
		System.out.println("Tested "+nTests+" locations");
		return list;
	}

	private boolean testPatch(int x, int y, int w) {

		f.setROI(x, y, w);

		double sumH = 0;
		double sumA = 0;
		int n = 0;
		int nLayers = classifier.cascadeLevels.size();
		
		for (int l=0;l<nLayers;l++) {

			while(n<classifier.cascadeLevels.get(l)) {
				WeakClassifier c = classifier.weakClassifiers.get(n);
				if(c.parity*f.computeFeature(c.index) > c.parity*c.thld) {
					sumH += c.alpha;
				}
				sumA += c.alpha;
				
				n++;
			}
			
			double thld_adj = classifier.cascadeThlds.get(l);
			if(sumH<sumA/2*thld_adj*thld_gain) {
				return false;
			}

		}
		
		return true;
	}

	static void drawBoundingBoxes(BufferedImage srcImage, List<Detection> list, double scaleFactor, ImageIcon icon) {

		int width = srcImage.getWidth();
		int height = srcImage.getHeight();
		Color color = new Color(255, 0, 0);
		int rgb = color.getRGB();  

		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		img.getGraphics().drawImage(srcImage, 0, 0, null);

		for (Detection d : list) {
			for (int x=d.x;x<d.x+d.w;x++) {
				img.setRGB(x, d.y, rgb);
			}
			for (int x=d.x;x<d.x+d.w;x++) {
				img.setRGB(x, d.y+d.w, rgb);
			}
			for (int y=d.y;y<d.y+d.w;y++) {
				img.setRGB(d.x, y, rgb);
			}
			for (int y=d.y;y<d.y+d.w;y++) {
				img.setRGB(d.x+d.w, y, rgb);
			}
		}
		
		icon.setImage(img.getScaledInstance(
								(int)(width*scaleFactor), (int)(height*scaleFactor), Image.SCALE_SMOOTH));

	}
}
