package app;

import javax.swing.SwingUtilities;

public class Application {
	static Drawer drawer;
	static Tree tree;

	public static void main(String[] args) {

		// TODO Auto-generated method stub
		if (args.length != 1) {
			System.out.println("Only specify the filename, exiting...");
			return;
		}
		RRTGenerator generator = new RRTGenerator(args[0]);
		// generator.printPoints();
		Application.tree = generator.tree;
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {

				drawer = new Drawer(tree);
				drawer.setVisible(true);
			}
		});

		generator.generate();
		// tree.printTree();

	}

}
