package code.classes;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * <h1>ASTProject</h1>
 * This class is used to manage projects using {@link ASTEntity}.
 * It contains functionality which allows scanning {@link ASTEntity#getImplementation()} for usage of {@link ASTEntity} instances
 * loaded. Code needs to be bug-free libraries and files are allowed to missing.
 * <br/>
 * <i>Warning: employed heuristics may fail to identify variable types which employ multiple heuristics</i>
 * @author Manios Kranasakis
 */
public class ASTProject {
	private HashMap<String, ASTEntity> projectClasses = new HashMap<String, ASTEntity>();
	private ArrayList<ASTEntity> allMethods = new ArrayList<ASTEntity>();
	private HashMap<ASTEntity, Integer> allMethodsIds = new  HashMap<ASTEntity, Integer>();
	
	public ASTProject(String projectPath) {
		importPath(projectPath);
		updateProject();
	}
	
	public void updateProject() {
		allMethodsIds.clear();
		allMethods.clear();
		for(ASTEntity projectClass : projectClasses.values()) {
			for(Node method : projectClass.collapse()) 
			if(((ASTEntity)method).isMethod()){
				allMethodsIds.put((ASTEntity)method, allMethods.size());
				allMethods.add((ASTEntity)method);
			}
		}
	}
	
	public ASTEntity searchForMethod(String methodName) {
		for(ASTEntity method : allMethods) {
			if(method.getStackTrace().contains(methodName))
				return method;
		}
		return null;
	}
	
	public double[][] generateTraversalMatrix() {
		int N = allMethods.size(); 
		double[][] M = new double[N][N];
		for(int i=0;i<N;i++) {
			M[i][i] = 1;
			for(ASTEntity entity : getCalledMethodsBy(allMethods.get(i)))
				if(entity.isMethod() && allMethods.contains(entity)) {
					M[i][allMethodsIds.get(entity)] = 1;
				}
		}
		return M;
	}
	
	public ArrayList<ASTEntity> getCalledMethodsBy(ASTEntity method) {
		HashMap<String, ASTEntity> variableClasses = new HashMap<String, ASTEntity>();
		
		//identify variable declaration statements
		for(String classDeclarationStatement : getTopLevelStatements(((ASTEntity)method.getParent()).getImplementation(), ((ASTEntity)method.getParent()).getImplementation().indexOf('{')+1)) {
			if(classDeclarationStatement.indexOf("=")!=-1)
				classDeclarationStatement = classDeclarationStatement.substring(0, classDeclarationStatement.indexOf("="));
			if(!classDeclarationStatement.contains("(")) {
				String[] words = classDeclarationStatement.split("(\\s|\\<|\\>|\\,)+");//TODO search for known variable types in the words instead
				if(words.length>=2) {
					String variableName = words[words.length-1];
					String variableType = words[words.length-2];
					//handle hashmaps, lists, etc
					if(projectClasses.get(variableType)!=null) 
						variableClasses.put(variableName, projectClasses.get(variableType));
				}
			}
		}

		ArrayList<ASTEntity> ret = new ArrayList<ASTEntity>();
		for(String statement : splitToStatements(method.getImplementation().trim(), 1))
			for(ASTEntity statementCall : getStatementCalls(statement, variableClasses, (ASTEntity)method.getParent(), (ASTEntity)method.getParent()))
				if(!ret.contains(statementCall))
					ret.add(statementCall);
		return ret;
	}
	

	private static int topLevelIndexOf(String text, String str, int startingPosition) {
		char first = str.charAt(0);
		int pos = startingPosition-1;
		while(pos<text.length()) {
			pos = topLevelIndexOf(text, first, pos+1);
			if(pos==-1)
				break;
			boolean found = true;
			for(int i=0;i<str.length() && found;i++)
				if(pos+i>=text.length())
					found = false;
				else if(str.charAt(i)!=text.charAt(pos+i))
					found = false;
			if(found)
				return pos;
		}
		return -1;
	}
	
	private static int topLevelIndexOf(String text, char c, int pos) {
		int level = 0;
		for(int i=pos;i<text.length();i++) {
			if(text.charAt(i)==c && level==0)
				return i;
			if(text.charAt(i)=='(')
				level++;
			else if(text.charAt(i)==')')
				level--;
			if(text.charAt(i)==c && level==0)
				return i;
			if(level<0)
				break;
		}
		return -1;
	}
	
	private static ArrayList<String> getTopLevelStatements(String text, int pos) {
		ArrayList<String> statements = new ArrayList<String>();
		String currentStatement = "";
		int level = 0;
		for(int i=pos;i<text.length();i++) {
			char c = text.charAt(i);
			if(c=='{')
				level++;
			else if(c=='}')
				level--;
			else if(c=='\n')
				continue;
			else if(c==';' && level==0) {
				statements.add(currentStatement.trim());
				currentStatement = "";
			}
			else if(level==0)
				currentStatement += c;
			if(level<0)
				break;
		}
		currentStatement = currentStatement.trim();
		if(!currentStatement.isEmpty())
			statements.add(currentStatement);
		return statements;
	}
	
	private static ArrayList<String> splitToStatements(String text, int pos) {
		ArrayList<String> statements = new ArrayList<String>();
		String currentStatement = "";
		int level = 0;
		for(int i=pos;i<text.length();i++) {
			char c = text.charAt(i);
			if(c=='\n')
				continue;
			else if(c=='{' || c=='}' || c==';') {
				statements.add(currentStatement.trim());
				currentStatement = "";
			}
			else
				currentStatement += c;
			if(level<0)
				break;
		}
		currentStatement = currentStatement.trim();
		if(!currentStatement.isEmpty())
			statements.add(currentStatement);
		return statements;
	}
	
	private static int topLevelCountOf(String text, char c, int pos) {
		int level = 0;
		int count = 0;
		for(int i=pos;i<text.length();i++) {
			if(text.charAt(i)=='(')
				level++;
			else if(text.charAt(i)==')')
				level--;
			else if(text.charAt(i)==c && level==0)
				count++;
			if(level<0)
				break;
		}
		return count;
	}
	
	private ArrayList<ASTEntity> getStatementCalls(String statement, HashMap<String, ASTEntity> variableClasses, ASTEntity parentEntity, ASTEntity defaultParentEntity) {
		statement = statement.trim();
		ArrayList<ASTEntity> calls = new ArrayList<ASTEntity>();
		//System.out.println("--->"+statement);
		
		int pos = 0;
		while(pos<statement.length()) {
			int idxEquals = topLevelIndexOf(statement, '=', pos);
			if(idxEquals!=-1 && statement.charAt(idxEquals-1)=='!')
				idxEquals = -1;
			if(idxEquals!=-1 && idxEquals<statement.length()-1 && statement.charAt(idxEquals+1)=='=')
				idxEquals = -1;
			if(idxEquals!=-1) {
				String LHStext = statement.substring(0, idxEquals).trim();
				ArrayList<ASTEntity> RHSCalls = getStatementCalls(statement.substring(idxEquals+1), variableClasses, parentEntity, defaultParentEntity);
				calls.addAll(getStatementCalls(LHStext, variableClasses, parentEntity, defaultParentEntity));
				calls.addAll(RHSCalls);
				if(RHSCalls.size()>=1)  {
					String variableName = LHStext.substring(LHStext.lastIndexOf(' ')+1);
					String variableType = RHSCalls.get(RHSCalls.size()-1).getType();
					if(projectClasses.get(variableType)!=null)
						variableClasses.put(variableName, projectClasses.get(variableType));
				}
				pos = idxEquals+1;
			}
			else {
				String entityText = statement.substring(pos);
				ArrayList<ASTEntity> found = recognizeKnownEntity(entityText, parentEntity, variableClasses, defaultParentEntity);
				if(!found.isEmpty())
					parentEntity = found.get(found.size()-1);
				calls.addAll(found);
				break;
			}
		}
		
		return calls;
	}
	
	private ArrayList<ASTEntity> recognizeKnownEntity(String callText, ASTEntity parentEntity,  HashMap<String, ASTEntity> variableClasses, ASTEntity defaultParentEntity) {
		ArrayList<ASTEntity> ret = new ArrayList<ASTEntity>();
		callText = callText.trim();
		//while(callText.startsWith("(") && callText.endsWith(")")) 
			//callText = callText.substring(1, callText.length()-1).trim();
		//System.out.println("--->"+callText);
		if(!callText.contains(")")) {//static class references
			if(projectClasses.get(callText)!=null)
				ret.add(projectClasses.get(callText));
		}
		else if(callText.startsWith("new ")) {//constructors
			int idx = callText.indexOf("(");
			int nArgs = topLevelCountOf(callText, ',', idx+1)+1;
			if(callText.substring(idx+1, topLevelIndexOf(callText,')', idx)).trim().isEmpty())
				nArgs = 0;
			String className = callText.substring(4, idx).trim();
			parentEntity = projectClasses.get(className);
			ASTEntity foundConstructor = null;
			if(parentEntity!=null)
				for(Node entity : parentEntity.getChildren())
					if(entity.getName().equals(className) && entity.getChildren().size()==nArgs)
						foundConstructor = (ASTEntity)entity;
			if(foundConstructor!=null)
				ret.add(foundConstructor);
		}
		else {
			int idx = topLevelIndexOf(callText, '(', 0);
			if(idx==-1)
				idx = callText.length()-1;
			int idxEnd = topLevelIndexOf(callText, ')', idx);
			int pos;
			if((pos = topLevelIndexOf(callText, "&&", 0))!=-1) {
				if(topLevelIndexOf(callText, "&&", 0)<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+2), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, "||", 0))!=-1) {
				if(pos<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+2), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, "==", 0))!=-1) {
				if(pos<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+2), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, "!=", 0))!=-1) {
				if(pos<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+2), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, '+', 0))!=-1) {
				if(pos<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+1), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, '-', 0))!=-1) {
				if(pos<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+1), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, '*', 0))!=-1) {
				if(pos<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+1), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, '/', 0))!=-1) {
				if(pos<callText.length()-1) {
					ret.addAll(getStatementCalls(callText.substring(0, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					ret.addAll(getStatementCalls(callText.substring(pos+1), variableClasses, defaultParentEntity, defaultParentEntity));
				}
			}
			else if(idxEnd!=callText.length()-1 && topLevelIndexOf(callText, ' ', 0)!=-1) {
				ret.addAll(recognizeKnownEntity(callText.substring(0, idx), defaultParentEntity, variableClasses, defaultParentEntity));
				if(idxEnd!=-1) {
					ret.addAll(recognizeKnownEntity(callText.substring(idx+1, idxEnd), defaultParentEntity, variableClasses, defaultParentEntity));
					ret.addAll(recognizeKnownEntity(callText.substring(idxEnd+1), defaultParentEntity, variableClasses, defaultParentEntity));
				}
			}
			else if((pos = topLevelIndexOf(callText, '.', 0))!=-1) {
				String entityText = callText.substring(0, pos).trim();
				if(variableClasses.get(entityText)!=null) {
					parentEntity = variableClasses.get(entityText);
					ret.add(parentEntity);
					//System.out.println("Variable : "+entityText+" -> "+parentEntity.getStackTrace());
				}
				else {
					ArrayList<ASTEntity> found = recognizeKnownEntity(entityText, parentEntity, variableClasses, defaultParentEntity);
					if(!found.isEmpty() && found.get(found.size()-1).isClass())
						parentEntity = found.get(found.size()-1);
					else if(!found.isEmpty() && projectClasses.get(found.get(found.size()-1).getType())!=null) {
						parentEntity = projectClasses.get(found.get(found.size()-1).getType());
						//System.out.println("Return : "+parentEntity.getStackTrace());
					}
					//else
						//System.out.println("Return : unchanged for "+entityText);
					ret.addAll(found);
				}
				ret.addAll(recognizeKnownEntity(callText.substring(pos+1), parentEntity, variableClasses, defaultParentEntity));
			}
			else {
				int nArgs = topLevelCountOf(callText, ',', idx+1)+1;
				if(idxEnd==-1 || idx>=callText.length()-1 || callText.substring(idx+1, idxEnd).trim().isEmpty())
					nArgs = 0;
				String methodName = callText.substring(0, idx).trim();
				ASTEntity foundMethod = null;
				if(parentEntity!=null)
					for(Node entity : parentEntity.getChildren())
						if(entity.getName().equals(methodName) && entity.getChildren().size()==nArgs)
							foundMethod = (ASTEntity)entity;
				for(int i=0;i<nArgs;i++) {
					pos = topLevelIndexOf(callText, ',', idx+1);
					if(pos==-1)
						pos = idxEnd;
					ret.addAll(getStatementCalls(callText.substring(idx+1, pos), variableClasses, defaultParentEntity, defaultParentEntity));
					idx = pos;
				}
				if(foundMethod!=null)
					ret.add(foundMethod);
				else
					ret.add(parentEntity);
			}
		}
		return ret;
	}
	

	public void importPath(String path) {
	    File directory = new File(path);
	    File[] fList = directory.listFiles();
	    for (File file : fList) {
	        if (file.isFile()) {
	            importFile(file.getPath());
	        } 
	        else if (file.isDirectory()) {
	        	importPath(file.getPath());
	        }
	    }
	}
	
	public void importFile(String path) {
		if(!path.endsWith(".java"))
			return;
		try {
			addClassObject(new ClassObject(path));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public void addClassObject(ClassObject classObject) {
		for(Node entity : classObject.getRoot().collapse())
			if(((ASTEntity)entity).isClass())
				projectClasses.put(entity.getStackTrace(), (ASTEntity)entity);
	}
}
