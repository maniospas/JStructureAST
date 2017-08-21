import java.util.ArrayList;

import code.classes.ASTEntity;
import code.classes.ASTProject;

public class StackTraceMain {
	
	public static String convertNameToText(String name) {
		StringBuilder text = new StringBuilder();
		for(String word : name.split("(?=\\p{Lu})|\\s+"))
			text.append(word.toLowerCase()).append(" ");
		return text.toString().trim();
	}
	
	public static String produceMethodDescription(ASTProject project, ASTEntity method, double[] callFrequencies, double accumulatedProbability) {
		String ret = "";
		int position = 0;
		ArrayList<ASTEntity> dependecies = project.getCalledMethodsBy(method);
		for(ASTEntity dependency : dependecies) 
			if(dependency.isMethod()){
				double prob = accumulatedProbability*Math.exp(position/(double)dependecies.size()-1);
				prob *= Math.exp(-(double)callFrequencies[project.getIndexInTraversalMatrix(dependency)]);
				System.out.println(accumulatedProbability+" -> "+dependency.getStackTrace()+" : "+prob);
				if(prob>0.01) {
					if(dependency.getType().equals("constructor"))
						ret += "\ncreate new "+convertNameToText(dependency.getName());
					else
						ret += "\n"+convertNameToText(dependency.getName())+" "+produceMethodDescription(project, dependency, callFrequencies, prob);
				}
			}
		return ret.trim();
	}

	public static void main(String[] args) {
		ASTProject project = new ASTProject("../RTS/");
		ASTEntity method = project.searchForMethod("GamePanel.process");
		double[] ranks = PageRank.obtainRanks(project.generateTraversalMatrix());
		double maxRank = 0;
		for(double rank : ranks)
			maxRank += rank;
		for(int i=0;i<ranks.length;i++)
			ranks[i] *= ranks.length/maxRank;
		System.out.println(produceMethodDescription(project, method, ranks, 1));
		
		/*for(ASTEntity dependency : project.getCalledMethodsBy(method)) {
			System.out.println(dependency.getStackTrace());
		}*/
	}

}
