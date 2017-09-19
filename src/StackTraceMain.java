import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import javax.imageio.ImageIO;

import code.classes.ASTEntity;
import code.classes.ASTProject;
import code.classes.FlowControl;

public class StackTraceMain {
	
	private static final String [] adjectiveVerbs = {"is", "has"};
	
	//private static String reorderSentenceStructure(String text) {
		
	//}
	
	private static String convertNameToText(String name) {
		StringBuilder text = new StringBuilder();
		String[] words = name.replaceAll("[0-9]+", "").split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])|\\.");//credit: https://stackoverflow.com/users/367273/npe
		if(words.length>=1) {
			String firstWord = words[0];
			if(firstWord.equalsIgnoreCase("to"))
				text.append("convert ");
			if(firstWord.equalsIgnoreCase("from"))
				text.append("convert ");
		}
		for(String word : words) {
			if(word.toUpperCase().equals(word))
				text.append(word).append(" ");
			else
				text.append(word.toLowerCase()).append(" ");
		}
		return text.toString().trim();
	}
	
	public static String convertToText(ASTEntity method, FlowControl statement, double depth) {
		StringBuilder text = new StringBuilder();
		/*for(ASTEntity calledEntity : project.getCalledMethodsBy(method, "{"+statement.getContents()+"}", variableClasses))
			if(calledEntity.isMethod())
				text.append(convertNameToText(calledEntity.getStackTrace())).append(" ");*/
		String statementText = statement.getContents()
				.replace("=", " = ")
				.replace("!", " ! ")
				.replace("this.", method.getParent().getName()+" ")
				.replaceAll("=\\s*=", "==")
				.replaceAll("\\<\\s*\\=", "<=")
				.replaceAll("\\>\\s*\\=", ">=")
				.replaceAll("\\!\\s*\\=", "!=")
				.replace("&&", " and ")
				.replace("||", " or ")
				.replace(" ! ", " not ")
				.replaceAll("\\(.*\\)", "");
		if(statementText.contains(" = ")) {
			String assignment = convertNameToText(statementText.substring(3+statementText.indexOf(" = ")));
			if(assignment.contains("+") || assignment.contains("-") || assignment.contains("?") || assignment.contains("*") || assignment.contains("-") || assignment.contains("(") || assignment.contains(")") || assignment.contains("/") || assignment.contains("%"))
				text.append("set ").append(convertNameToText(statementText.substring(0,statementText.indexOf(" = "))));
			else if(assignment.length()<3)
				text.append("set ").append(convertNameToText(statementText.substring(0,statementText.indexOf(" = "))));
			else
				text.append(assignment);
		}
		else if(statementText.contains(".") && (statementText.contains(".is") || statementText.contains(".has"))) {
			text.append(convertNameToText(statementText.substring(1+statementText.lastIndexOf(".")))).append(" ").append(convertNameToText(statementText.substring(0,statementText.lastIndexOf("."))));
		}
		else if(statementText.contains(".")) {
			text.append(convertNameToText(statementText.substring(1+statementText.lastIndexOf("."))));
		}
		else
			text.append(convertNameToText(statementText));
		String ret = text.toString().trim();
		if(!ret.contains(" "))
			ret = convertNameToText(method.getParent().getName())+" "+ret;
		return ret.replace("new ", "create ").replaceAll("\\s+", " ");
	}
	
	public static String produceMethodDescription(ASTEntity method, FlowControl flowControl, HashMap<String, Double> ranks, double depth) {
		String delimiter = depth==0?"\n":", ";
		StringBuilder description = new StringBuilder();
		for(FlowControl child : flowControl.getChildren()) {
			if(child.isStatement()) {
				String text = convertToText(method, child, depth);
				if(!text.isEmpty()) {
					if(description.length()!=0)
						description.append(delimiter);
					description.append(text);
				}
			}
			else {
				String text = produceMethodDescription(method, child, ranks, depth+1);
				if(!text.isEmpty()) {
					if(description.length()!=0)
						description.append(delimiter);
					if(depth==0 && (child.getType().equals("for") || child.getType().equals("while"))) {
						String conditionText = convertToText(method, child.getCondition(), depth);
						if(conditionText.startsWith("not "))
							description.append("until ").append(conditionText.substring(4).trim()).append(": ").append(text);
						else
							description.append("while ").append(conditionText).append(": ").append(text);
					}
					else
						description.append(text);
				}
			}
		}
		return description.toString();
	}
	
	private static HashMap<String, Double> obtainMethodRanks(ASTProject project) {
		double[][] traversalMatrix = project.generateTraversalMatrix();
		double[] ranks = PageRank.obtainRanks(traversalMatrix);
		double maxRank = 0;
		for(double rank : ranks)
			//if(rank>maxRank)
				maxRank += rank;
		for(int i=0;i<ranks.length;i++)
			ranks[i] *= 1.0/maxRank;
		
		try {
		    BufferedImage image = new BufferedImage(traversalMatrix.length, traversalMatrix.length,
                    BufferedImage.TYPE_INT_ARGB);
		    for(int i=0; i<traversalMatrix.length; i++) {
		        for(int j=0; j<traversalMatrix.length; j++) {
		            int a = (int)(255*traversalMatrix[i][j]);
		            Color newColor = new Color(a,a,a);
		            image.setRGB(j,i,newColor.getRGB());
		        }
		    }
		    File output = new File("GrayScale.jpg");
		    ImageIO.write(image, "jpg", output);
		}
		catch(Exception e) {}
		
		HashMap<String, Double> methodNameRanks = new HashMap<String, Double>();
		for(int i=0;i<ranks.length;i++)
			methodNameRanks.put(project.getMethods().get(i).getName(), ranks[i]);
		return methodNameRanks;
	}

	public static void main(String[] args) {
		//ASTProject project = new ASTProject("../lwjgl-water-shader-master/");
		//ASTEntity method = project.searchForMethod("Renderer.Renderer");
		ASTProject project = new ASTProject("../KingClashers/");
		ASTEntity method = project.searchForMethod("KingClashersGame.main");
		
		
		System.out.println("\n----------------------------------");
		System.out.println(produceMethodDescription(method, new FlowControl(method.getImplementation()), obtainMethodRanks(project), 0));
		
		/*for(ASTEntity dependency : project.getCalledMethodsBy(method)) {
			System.out.println(dependency.getStackTrace());
		}*/
		
	}

}
