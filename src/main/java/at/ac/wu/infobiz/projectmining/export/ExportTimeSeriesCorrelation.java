package at.ac.wu.infobiz.projectmining.export;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.math.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.joda.time.DateTime;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import at.ac.wu.infobiz.projectmining.timeseries.FileStory;
import at.ac.wu.infobiz.projectmining.timeseries.FileStoryRecord;
import at.ac.wu.infobiz.projectmining.timeseries.TimeSeriesTable;
import at.ac.wu.infobiz.projectmining.timeseries.TreeDistance;

public class ExportTimeSeriesCorrelation {

	private static final double STRENGTH_THRESHOLD = 0.7;
	private static final double WEAK_THRESHOLD = 0.3;
	private static final String SEP = ";";
	//#Artifacts	#Users	#Containments	#MaxPossibleContainments	
//	#Strong Dependencies	#Weak Dependencies	#Intra-Containment Dependencies	
//	#Inter-Containment Dependencies	#Strong Inter-Containment Dependencies	
//	AvgTreeDepth	MaxTreeDepth	#AvgActivitiesPerProcess	#AvgUsersPerArtifact
	private static int MAX_DISTANCE = 0;
	
	public static int ARTIFACTS;
//	public static int USERS;
	public static int CONTAINMENTS;
	public static int MAX_CONTAINMENTS;
	public static int STRONG_DEPENDENCIES;
	public static int WEAK_DEPENDENCIES;
	public static int INTRA_CONTA_DEPEND;
	public static int WEAK_INTRA_CONTA_DEPEND;
	public static int STRONG_INTRA_CONTA_DEPEND;
	public static int INTER_CONTA_DEPEND;
	public static int STRONG_INTER_CONTA_DEP;
	public static int WEAK_INTER_CONTA_DEP;
	public static double AVG_TREE_DEPTH;
	public static double MAX_TREE_DEPTH;
	public static double AVG_ACITIVIES_PROCESS;
	public static double AVG_USER_ARTIFACT;
	public static double MAX_TREE_DISTANCE;
	public static double AVG_TREE_DISTANCE;


	private static double computeAverageProcessLength(Map<File, FileStory> map) {
		Set<File> files = map.keySet();
		DescriptiveStatistics ds = new DescriptiveStatistics();
		for (File f : files) {
			FileStory fs = map.get(f);
			List<FileStoryRecord> fileStoryRecords = fs.story;
			for (FileStoryRecord fileStoryRecord : fileStoryRecords) {
				String[] comments = fileStoryRecord.comments.split("§");
				if(comments.length>0 && !comments[0].isEmpty())
					ds.addValue(comments.length);
			}
		}
		return ds.getMean();
	}

	private static int[][] computeTreeDistances(Map<String, String> namesMap) {
		int[][] distanceMatrix = new int[namesMap.size()][namesMap.size()];
		List<String> filenames = new ArrayList<String>(namesMap.values());
		for (int i = 0; i < distanceMatrix.length; i++) {				
			for(int j=i+1; j<distanceMatrix[0].length; j++){
				File f1 = new File(filenames.get(i));
				File f2 = new File(filenames.get(j));
				int c = TreeDistance.lca(f1.getAbsolutePath(), f2.getAbsolutePath());				
				distanceMatrix[i][j] = c;
				if(MAX_DISTANCE < c)
					MAX_DISTANCE = c;
			}
		}
		return distanceMatrix;
	}
	
	private static double computeUsersPerFile(Map<File, FileStory> map) {
		Set<File> files = map.keySet();
		DescriptiveStatistics ds = new DescriptiveStatistics();
		for (File f : files) {
			FileStory fs = map.get(f);
			List<FileStoryRecord> fileStoryRecords = fs.story;
			for (FileStoryRecord fileStoryRecord : fileStoryRecords) {
				if(fileStoryRecord.getUsers().length>0)
					ds.addValue(fileStoryRecord.getUsers().length);
			}
		}
		return ds.getMean();
	}

	private static Double correlation(FileStory fileStory1, FileStory fileStory2, int field) throws FileNotFoundException {
		double[] fs1 = filterBy(fileStory1, field);
		double[] fs2 = filterBy(fileStory2,field);
				
		PearsonsCorrelation p = new PearsonsCorrelation();
		return new Double(p.correlation(fs1, fs2));
	}
	
	/**
	 * @param folder
	 * @param outFile
	 * @param totalsFile 
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	private static void doAnalysisForFolderOld(String folder, String outFile, File totalsFile) throws FileNotFoundException, IOException {
		long start = System.currentTimeMillis();
		System.out.println();
		System.out.println("Reading stories from folder: "+folder);

		List<File> allFiles = listFilesForFolder(new File(folder));
		Map<File, FileStory> map = getAllFileStories(allFiles);
		
		for (File file : map.keySet()) {
			Collections.sort(map.get(file).story);
		}
		
		TimeSeriesTable tst = new TimeSeriesTable(map);
		map = tst.getExpandedFileStoryMap();
//		System.out.println(map);

		Map<String, String> namesMap = loadNamesMap(folder.split("-")[0]+"-namesMap/namesMap.csv");

		System.out.println("Computing correlations.");
		double[][] correlationMatrix = doPairWiseCorrelations2(namesMap, map, 3);
		
//		System.out.println(map.size()+" Total files");
		ARTIFACTS = map.size();
		
		System.out.println("Computing containments.");
//		boolean[][] containmentsMatrix = doPairWiseContainmentsCheck(map);
		int[][] treeDistance = computeTreeDistances(namesMap);
//		boolean[][] containmentsMatrix = doPairWiseContainmentsCheck2(namesMap);
		
		printCorrelationMatrix(correlationMatrix, map, "correlations/"+outFile+"old.csv");
//		printContainmentsMatrix2(containmentsMatrix, namesMap, "containments/"+outFile);
		printDistanceMatrix(treeDistance, map, "distances/"+outFile);
		
		System.out.println("Generating Correlations VS Distance Table. Writing into file correlationsVersusDistanceOld.csv");
		printCorrelationVersusDistance(correlationMatrix, treeDistance, map,"correlationsVersusDistanceOld/"+outFile+"Old.csv");
		
		
//		System.out.println(computeUsersPerFile(map) + " average users per file");
//		System.out.println(computeAverageProcessLength(map)+ " average process length");
		
		AVG_USER_ARTIFACT = computeUsersPerFile(map);
		AVG_ACITIVIES_PROCESS = computeAverageProcessLength(map);
		
		System.out.println("Done. "+(System.currentTimeMillis()-start)/1000.0+ " Sec.");
		System.out.println("Results written into: "+outFile);
		
		System.out.println("ARTIFACTS"+SEP+"CONTAINMENTS"+SEP+"STRONG_DEPENDENCIES"+SEP+"WEAK_DEPENDENCIES"+SEP+""
				+ "INTRA_CONTA_DEPEND"+SEP+" WEAK_INTRA_CONTA_DEPEND"+SEP+" STRONG_INTRA_CONTA_DEPEND"+SEP+" "
				+ "INTER_CONTA_DEPEND"+SEP+"WEAK_INTER_CONTA_DEP"+SEP+" STRONG_INTER_CONTA_DEP"+SEP+""
				+ "AVG_TREE_DEPTH"+SEP+" MAX_TREE_DEPTH"+SEP+""
				+ "AVG_ACITIVIES_PROCESS"+SEP+"AVG_USER_ARTIFACT" + SEP 
				+ "AVG_TREE_DISTANCE"+SEP+"MAX_TREE_DISTANCE");
		System.out.println(folder+SEP+""+SEP+""+SEP+""+SEP+ARTIFACTS+SEP+CONTAINMENTS+SEP+
				STRONG_DEPENDENCIES+""+SEP+WEAK_DEPENDENCIES+SEP+INTRA_CONTA_DEPEND+SEP+WEAK_INTRA_CONTA_DEPEND+SEP+
				STRONG_INTRA_CONTA_DEPEND+SEP+INTER_CONTA_DEPEND+SEP+WEAK_INTER_CONTA_DEP+SEP+STRONG_INTER_CONTA_DEP+SEP+
				AVG_TREE_DEPTH+SEP+MAX_TREE_DEPTH+SEP+
				AVG_ACITIVIES_PROCESS+SEP+AVG_USER_ARTIFACT + SEP +
				+ AVG_TREE_DISTANCE+SEP+MAX_TREE_DISTANCE);
		
		appendToFile(totalsFile, folder.split("-")[0]+SEP+ARTIFACTS+SEP+CONTAINMENTS+SEP+
				STRONG_DEPENDENCIES+""+SEP+WEAK_DEPENDENCIES+SEP+INTRA_CONTA_DEPEND+SEP+WEAK_INTRA_CONTA_DEPEND+SEP+
				STRONG_INTRA_CONTA_DEPEND+SEP+INTER_CONTA_DEPEND+SEP+WEAK_INTER_CONTA_DEP+SEP+STRONG_INTER_CONTA_DEP+SEP+
				AVG_TREE_DEPTH+SEP+MAX_TREE_DEPTH+SEP+
				AVG_ACITIVIES_PROCESS+SEP+AVG_USER_ARTIFACT + SEP +
				+ AVG_TREE_DISTANCE+SEP+MAX_TREE_DISTANCE);
	}

	/**
	 * @param folder
	 * @param outFile
	 * @param totalsFile 
	 * @throws IOException 
	 * @throws FileNotFoundException 
	 */
	private static void doAnalysisForFolder(String folder, String outFile, File totalsFile) throws FileNotFoundException, IOException {
		long start = System.currentTimeMillis();
		System.out.println();
		System.out.println("Reading stories from folder: "+folder);

		List<File> allFiles = listFilesForFolder(new File(folder));
		Map<File, FileStory> map = getAllFileStories(allFiles);
		
//		Map<File, FileStory> noExpandedFileStoryMap = createCopy(map);
		
//		for (File file : map.keySet()) {
//			Collections.sort(map.get(file).story);
//		}
		
//		TimeSeriesTable tst = new TimeSeriesTable(map);
//		map = tst.getExpandedFileStoryMap();
//		
//		for (File file : map.keySet()) {
//			System.out.println("map.size()="+map.get(file).story.size()+" noExpandedFileStoryMap.size()="+noExpandedFileStoryMap.get(file).story.size());
//		}		

		Map<String, String> namesMap = loadNamesMap(folder.split("-")[0]+"-namesMap/namesMap.csv");

		System.out.println("Computing correlations.");
		
//		double[][] correlationMatrix = doPairWiseCorrelations2(namesMap, map, 3);
		
		double[][] correlationMatrix = doPairWiseCorrelations(namesMap, map, 3);
		
//		System.out.println(map.size()+" Total files");
		ARTIFACTS = map.size();
		
		System.out.println("Computing containments.");
//		boolean[][] containmentsMatrix = doPairWiseContainmentsCheck(map);
		int[][] treeDistance = computeTreeDistances(namesMap);
//		boolean[][] containmentsMatrix = doPairWiseContainmentsCheck2(namesMap);
		
		printCorrelationMatrix(correlationMatrix, map, "correlations/"+outFile);
//		printCorrelationMatrix(correlationMatrixNoExpanded, noExpandedFileStoryMap, "correlations/2-"+outFile);
		
//		printContainmentsMatrix2(containmentsMatrix, namesMap, "containments/"+outFile);
		printDistanceMatrix(treeDistance, map, "distances/"+outFile);
		
		System.out.println("Generating Correlations VS Distance Table. Writing into directory correlationsVersusDistance");
		printCorrelationVersusDistance(correlationMatrix, treeDistance, map,"correlationsVersusDistance/"+outFile);
		
//		System.out.println(computeUsersPerFile(map) + " average users per file");
//		System.out.println(computeAverageProcessLength(map)+ " average process length");
		
		AVG_USER_ARTIFACT = computeUsersPerFile(map);
		AVG_ACITIVIES_PROCESS = computeAverageProcessLength(map);
		
		System.out.println("Done. "+(System.currentTimeMillis()-start)/1000.0+ " Sec.");
		System.out.println("Results written into: "+outFile);
		
		System.out.println("ARTIFACTS"+SEP+"CONTAINMENTS"+SEP+"STRONG_DEPENDENCIES"+SEP+"WEAK_DEPENDENCIES"+SEP+""
				+ "INTRA_CONTA_DEPEND"+SEP+" WEAK_INTRA_CONTA_DEPEND"+SEP+" STRONG_INTRA_CONTA_DEPEND"+SEP+" "
				+ "INTER_CONTA_DEPEND"+SEP+"WEAK_INTER_CONTA_DEP"+SEP+" STRONG_INTER_CONTA_DEP"+SEP+""
				+ "AVG_TREE_DEPTH"+SEP+" MAX_TREE_DEPTH"+SEP+""
				+ "AVG_ACITIVIES_PROCESS"+SEP+"AVG_USER_ARTIFACT" + SEP 
				+ "AVG_TREE_DISTANCE"+SEP+"MAX_TREE_DISTANCE");
		
		System.out.println(folder+SEP+""+SEP+""+SEP+""+SEP+ARTIFACTS+SEP+CONTAINMENTS+SEP+
				STRONG_DEPENDENCIES+""+SEP+WEAK_DEPENDENCIES+SEP+INTRA_CONTA_DEPEND+SEP+WEAK_INTRA_CONTA_DEPEND+SEP+
				STRONG_INTRA_CONTA_DEPEND+SEP+INTER_CONTA_DEPEND+SEP+WEAK_INTER_CONTA_DEP+SEP+STRONG_INTER_CONTA_DEP+SEP+
				AVG_TREE_DEPTH+SEP+MAX_TREE_DEPTH+SEP+
				AVG_ACITIVIES_PROCESS+SEP+AVG_USER_ARTIFACT + SEP +
				+ AVG_TREE_DISTANCE+SEP+MAX_TREE_DISTANCE);
		
		appendToFile(totalsFile, folder.split("-")[0]+SEP+ARTIFACTS+SEP+CONTAINMENTS+SEP+
				STRONG_DEPENDENCIES+""+SEP+WEAK_DEPENDENCIES+SEP+INTRA_CONTA_DEPEND+SEP+WEAK_INTRA_CONTA_DEPEND+SEP+
				STRONG_INTRA_CONTA_DEPEND+SEP+INTER_CONTA_DEPEND+SEP+WEAK_INTER_CONTA_DEP+SEP+STRONG_INTER_CONTA_DEP+SEP+
				AVG_TREE_DEPTH+SEP+MAX_TREE_DEPTH+SEP+
				AVG_ACITIVIES_PROCESS+SEP+AVG_USER_ARTIFACT + SEP +
				+ AVG_TREE_DISTANCE+SEP+MAX_TREE_DISTANCE);

	}

	private static double[][] doPairWiseCorrelations2(Map<String, String> namesMap, Map<File, FileStory> map, int field) {
		List<File> files = new ArrayList<File>(map.keySet());
//		String[] header = new String[files.size()+1];
		double[][] correlationMatrix = new double[files.size()][files.size()];
		int strongCorCount = 0, corCount=0;
		int interContainmentCount = 0, weakInterContainmentCount = 0, strongInterContainmentCount = 0;
		int intraContainmentCount = 0, weakIntraContainmentCount = 0, strongIntraContainmentCount = 0;
		int weakCorCount = 0;
		for(int i=0; i<files.size(); i++){
			for(int j=i; j<files.size(); j++){
				FileStory fs1 = map.get(files.get(i));
				FileStory fs2 = map.get(files.get(j));
				if(fs1.story.size()!=fs2.story.size()){
					System.err.println("Size of stories not matching! "+files.get(i)+" has "+fs1.story.size()+ ""
							+ " and "+files.get(j)+ " has "+fs2.story.size());
					continue;
				}
				double cor = 0;
				try {
					File f1 = new File(namesMap.get(files.get(i).toString()));
					File f2 = new File(namesMap.get(files.get(j).toString()));
					cor = correlation(fs1, fs2, field);
					double corStrength = Math.abs(cor); 
					corCount++;
					
					boolean sameContainment = sameContainment(f1, f2);
					
					if(sameContainment){
						intraContainmentCount++;
						if(corStrength>STRENGTH_THRESHOLD){
							strongIntraContainmentCount++;
							strongCorCount++;
						}
						if(corStrength<WEAK_THRESHOLD){
							weakIntraContainmentCount++;
							weakCorCount++;
						}
					}
					else{
						interContainmentCount++;
						if(corStrength>STRENGTH_THRESHOLD){
							strongInterContainmentCount++;
							if(corStrength > .7){
//								System.out.println(f1 + " + "+ f2 + " corr = "+cor);
//								if(!f1.equals(f2) && TreeDistance.lca(f1.getAbsolutePath(), f2.getAbsolutePath()) > 2){
//									System.out.println("f1="+f1+" f2="+f2 +" Cor="+cor+ " LCA="+TreeDistance.lca(f1.getAbsolutePath(), f2.getAbsolutePath()));
//								}
							}
							strongCorCount++;
						}
						if(corStrength<WEAK_THRESHOLD){
							weakInterContainmentCount++;
							weakCorCount++;
						}
					}
						
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				correlationMatrix[i][j] = cor;
//				System.out.println(""+files.get(i).getName()+" & "+files.get(j).getName()+ " = "+cor);
			}
		}
		System.out.println(corCount +" total correlations");
		
		STRONG_DEPENDENCIES = strongCorCount;
		WEAK_DEPENDENCIES = weakCorCount;
		INTRA_CONTA_DEPEND = intraContainmentCount;
		STRONG_INTRA_CONTA_DEPEND = strongIntraContainmentCount;
		WEAK_INTRA_CONTA_DEPEND = weakIntraContainmentCount;
		INTER_CONTA_DEPEND = interContainmentCount;
		WEAK_INTER_CONTA_DEP = weakInterContainmentCount;
		STRONG_INTER_CONTA_DEP = strongInterContainmentCount;
		
		return correlationMatrix;
	}
	
	private static double[][] doPairWiseCorrelations(Map<String, String> namesMap, Map<File, FileStory> map, int field) {
		List<File> files = new ArrayList<File>(map.keySet());
//		String[] header = new String[files.size()+1];
		double[][] correlationMatrix = new double[files.size()][files.size()];
		int strongCorCount = 0, corCount=0;
		int interContainmentCount = 0, weakInterContainmentCount = 0, strongInterContainmentCount = 0;
		int intraContainmentCount = 0, weakIntraContainmentCount = 0, strongIntraContainmentCount = 0;
		int weakCorCount = 0;
		
		
		for(int i=0; i<files.size(); i++){
			correlationMatrix[i][i]=1;
			for(int j=i+1; j<files.size(); j++){
				FileStory fs1 = map.get(files.get(i));
				FileStory fs2 = map.get(files.get(j));		
				
				//add zero padding
				doZeroPadding(fs1,fs2);
				
				double cor = 0;
				try {
					File f1 = new File(namesMap.get(files.get(i).toString()));
					File f2 = new File(namesMap.get(files.get(j).toString()));
					cor = correlation(fs1, fs2, field);
					double corStrength = Math.abs(cor); 
					corCount++;
					
					boolean sameContainment = sameContainment(f1, f2);
					
					if(sameContainment){
						intraContainmentCount++;
						if(corStrength>STRENGTH_THRESHOLD){
							strongIntraContainmentCount++;
							strongCorCount++;
						}
						if(corStrength<WEAK_THRESHOLD){
							weakIntraContainmentCount++;
							weakCorCount++;
						}
					}
					else{
						interContainmentCount++;
						if(corStrength>STRENGTH_THRESHOLD){
							strongInterContainmentCount++;
							if(corStrength > .7){
//								System.out.println(f1 + " + "+ f2 + " corr = "+cor);
//								if(!f1.equals(f2) && TreeDistance.lca(f1.getAbsolutePath(), f2.getAbsolutePath()) > 2){
//									System.out.println("f1="+f1+" f2="+f2 +" Cor="+cor+ " LCA="+TreeDistance.lca(f1.getAbsolutePath(), f2.getAbsolutePath()));
//								}
							}
							strongCorCount++;
						}
						if(corStrength<WEAK_THRESHOLD){
							weakInterContainmentCount++;
							weakCorCount++;
						}
					}
						
					
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				correlationMatrix[i][j] = cor;
//				System.out.println(""+files.get(i).getName()+" & "+files.get(j).getName()+ " = "+cor);
			}
		}
		System.out.println(corCount +" total correlations");
		
		STRONG_DEPENDENCIES = strongCorCount;
		WEAK_DEPENDENCIES = weakCorCount;
		INTRA_CONTA_DEPEND = intraContainmentCount;
		STRONG_INTRA_CONTA_DEPEND = strongIntraContainmentCount;
		WEAK_INTRA_CONTA_DEPEND = weakIntraContainmentCount;
		INTER_CONTA_DEPEND = interContainmentCount;
		WEAK_INTER_CONTA_DEP = weakInterContainmentCount;
		STRONG_INTER_CONTA_DEP = strongInterContainmentCount;
		
		return correlationMatrix;
	}
	
	
	private static void doZeroPadding(FileStory fs1, FileStory fs2) {
		Set<Date> datesFs1 = fs1.getAllDates();
		Set<Date> datesFs2 = fs2.getAllDates();
		
		int initialSize = fs1.story.size();
		
		//symmetric difference 
		Set<Date> symmDiff = new HashSet<Date>(datesFs1);
		symmDiff.addAll(datesFs2);
		Set<Date> intersection = new HashSet<Date>(datesFs1);
		intersection.retainAll(datesFs2);
		symmDiff.removeAll(intersection);
		
		for (Date date : symmDiff) {
			if(!fs1.containsDate(date))
				fs1.addDateWithZeroChange(date);
			if(!fs2.containsDate(date))
				fs2.addDateWithZeroChange(date);
		}
		
		if(initialSize==fs1.story.size()){
			DateTime d1 = new DateTime(fs1.getMinDate());
			fs1.addDateWithZeroChange(d1.plusDays(1).toDate());
			fs2.addDateWithZeroChange(d1.plusDays(1).toDate());
		}
	}

	private static double[] filterBy(FileStory fileStory1, int field) {
		
		double[] values = new double[fileStory1.story.size()];
		List<FileStoryRecord> fileStoryRecords = fileStory1.story;
		int i = 0;
		for (FileStoryRecord fileStoryRecord : fileStoryRecords) {
			
			switch (field) {
			case 1:
				values[i++]=fileStoryRecord.getTotalLinesAdded();
				break;
			case 2:				
				values[i++]=fileStoryRecord.getTotalLinesRemoved();
				break;
			case 3:
				values[i++]=fileStoryRecord.getTotalChangeInTheDay();
				break;
			case 4:
				values[i++]=fileStoryRecord.getLinesUntilThisDay();
				break;
			case 5:
				values[i++]=fileStoryRecord.getTotalDiffInTheDay();
				break;

			default:
				break;
			}

		}
		return values;
	}
	
	public static Map<File, FileStory> getAllFileStories(List<File> allFiles){
		Map<File, FileStory> stories = new TreeMap<File,FileStory>();

		for (File file : allFiles) {
//			if(hasEmptyStory(file))
//				continue;
			String name = file.getName();
			FileStory fStory = new FileStory(name, getStoryDataForFile(file));
			stories.put(new File(name),fStory);
		}
		return stories;
	}

	public static List<FileStoryRecord> getStoryDataForFile(File file){
		List<FileStoryRecord> storyRecords =  new ArrayList<FileStoryRecord>();
//		System.out.println("Got file "+file);
		
		CSVReader reader = null;
		try {
			reader = new CSVReader(new FileReader(file), '\t');
			reader.readNext();
			String[] line;
			while ((line = reader.readNext()) != null) {
				String comment = line[0];
				String dateString = line[1];
				String tlA = line[2];
				String tlR = line[3];
				String tcD = line[4];
				String tdD = line[5];
				String lUT = line[6];
				String usersString = line[7];

				//convert
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd"); 
				Date date = df.parse(dateString);
				int totalLinesAdded = Integer.parseInt(tlA);
				int totalLinesRemoved = Integer.parseInt(tlR);
				int totalChangeInTheDay = Integer.parseInt(tcD);
				int totalDiffInTheDay = Integer.parseInt(tdD);
				int linesUntilThisDay = Integer.parseInt(lUT);
				String[] users = usersString.split(" . ");

				FileStoryRecord fsr = new FileStoryRecord(comment, date, totalLinesAdded, totalLinesRemoved, totalChangeInTheDay, totalDiffInTheDay, linesUntilThisDay, users);

				storyRecords.add(fsr);
			}
			reader.close();
		} catch (IOException | ParseException | ArrayIndexOutOfBoundsException e) {
			System.err.println("Problem when reading file "+file);
			e.printStackTrace();
		}

		return storyRecords;
	}

	public static List<File> listFilesForFolder(File folder) {
		List<File> allFiles = new ArrayList<File>();
		for (final File fileEntry : folder.listFiles()) {
			if (fileEntry.isDirectory() && !fileEntry.isHidden()) {
				allFiles.addAll(listFilesForFolder(fileEntry));
			} else {
				//	            System.out.println(fileEntry.getName());
				allFiles.add(fileEntry);
			}
		}
		return allFiles;
	}

	/**
	 * @param path
	 * @param namesMap
	 * @return 
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static Map<String, String> loadNamesMap(String path)
			throws FileNotFoundException, IOException {
		Map<String, String> nm = new TreeMap<String, String>();
		CSVReader csvReader = new CSVReader(new FileReader(path), '\t');
		csvReader.readNext();//skip header
		List<String[]> names = csvReader.readAll();
		csvReader.close();
		
		//load map from names file
		for (String[] strings : names) {
			nm.put(strings[0], strings[1]);
		}
		
		return nm;
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		String folder = null;
		String outFile = null; 
		DateTime now = null;
		if(args.length == 1){
			folder = args[0];
//			folder = JOptionPane.showInputDialog("Stories-folder?");
			if(folder==null)
				System.exit(-1);
			now = DateTime.now();
			outFile = folder+"-"+now.getHourOfDay()+".csv";			
		}
		if(folder == null)
			System.exit(-1);
		
		File totalsFile  =  new File("allProjectsStatistics.csv");
		
		String header = "Project"+SEP+"ARTIFACTS"+SEP+"CONTAINMENTS"+SEP+"STRONG_DEPENDENCIES"+SEP+"WEAK_DEPENDENCIES"+SEP+""
		+ "INTRA_CONTA_DEPEND"+SEP+" WEAK_INTRA_CONTA_DEPEND"+SEP+" STRONG_INTRA_CONTA_DEPEND"+SEP+" "
		+ "INTER_CONTA_DEPEND"+SEP+"WEAK_INTER_CONTA_DEP"+SEP+" STRONG_INTER_CONTA_DEP"+SEP+""
		+ "AVG_TREE_DEPTH"+SEP+" MAX_TREE_DEPTH"+SEP+""
		+ "AVG_ACITIVIES_PROCESS"+SEP+"AVG_USER_ARTIFACT" + SEP 
		+ "AVG_TREE_DISTANCE"+SEP+"MAX_TREE_DISTANCE";
		
		if(!totalsFile.exists())
			appendToFile(totalsFile, header);
		
		System.out.println("Doing analysis for folder old way");
		doAnalysisForFolderOld(folder, outFile+"old.csv",new File("old-"+totalsFile.getName()));

		System.out.println("Doing analysis for folder optimized way");
		doAnalysisForFolder(folder, outFile, totalsFile);
		
		System.out.println("Summary in file "+totalsFile);
	}

	private static void appendToFile(File f, String line) {
		try {
			FileWriter fw = new FileWriter(f, true);
			PrintWriter pw = new PrintWriter(fw);
			pw.println(line);
			pw.close();
			fw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void printCorrelationMatrix(double[][] correlationMatrix, Map<File, FileStory> map, String filename){
		
		List<File> files = new ArrayList<File>(map.keySet());
		String[] header = new String[files.size()+1];
//		DecimalFormat df = new DecimalFormat("#.##");
//		df.setRoundingMode(RoundingMode.CEILING);
		
		try {
			File file = new File(filename);
			File absolute = file.getAbsoluteFile();
			absolute.getParentFile().mkdirs();
			FileWriter writer = new FileWriter(absolute, true);
			
			CSVWriter csvWriter = new CSVWriter(writer);
			header[0] = "Correlations";
			for(int i = 1; i<header.length;i++){
				header[i] = files.get(i-1).getName();
			}
			csvWriter.writeNext(header);
			
			List<String[]> csvRows = new ArrayList<String[]>();
			
			for(int i = 0; i<correlationMatrix.length; i++){
				String[] row = new String[correlationMatrix[i].length+1];
				row[0] = files.get(i).getName();
				for(int j=0;j<correlationMatrix[i].length; j++){
					double val = correlationMatrix[i][j];
//					row[j+1] = (val!=0)? ""+df.format(correlationMatrix[i][j]): "";
					row[j+1] = String.format("%.2f", val);
				}
				csvRows.add(row);
			}
			csvWriter.writeAll(csvRows);
			csvWriter.flush();
			csvWriter.close();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void printCorrelationVersusDistance(double[][] correlationMatrix, int[][] treeDistance,
			Map<File, FileStory> map, String filename) {
		
		ArrayList<File> files = new ArrayList<File>(map.keySet());
		String[] header = new String[]{"Pair", "Correlation", "Distance", "RelativeDistance", "FileName"};

		try{
			File file = new File(filename.substring(0, filename.indexOf('-'))+".csv");
			File absolute = file.getAbsoluteFile();
			absolute.getParentFile().mkdirs();
			if(absolute.exists())
				absolute.delete();
			FileWriter writer = new FileWriter(absolute, true);
			
			CSVWriter csvWriter = new CSVWriter(writer);
			csvWriter.writeNext(header); // write header
			
			List<String[]> csvRows = new ArrayList<String[]>();
			
			for(int r = 0; r<files.size(); r++){
				for(int c = r+1; c<files.size(); c++){
					String[] row = new String[5];
					row[0] = files.get(r).getName().replaceAll(".csv", "")+"-"+files.get(c).getName().replaceAll(".csv", "");
					row[1] = String.format("%.6f", correlationMatrix[r][c]);
					row[2] = treeDistance[r][c]+"";
					row[3] = String.format("%.6f",(double)treeDistance[r][c]/MAX_DISTANCE);
					row[4] = absolute.getName().replaceAll(".csv", "");
					csvRows.add(row);
				}
			}
			csvWriter.writeAll(csvRows);
			csvWriter.flush();
			csvWriter.close();
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void printDistanceMatrix(int[][] distanceMatrix, Map<File, FileStory> map,
			String filename) {
		List<File> files = new ArrayList<File>(map.keySet());
		String[] header = new String[files.size()+1];
		
//		for (int i = 0; i < distanceMatrix.length; i++) {
//			for (int j = 0; j < distanceMatrix.length; j++) {
//				System.out.print(distanceMatrix[i][j]+"\t");
//			}
//			System.out.println();
//		}
		
		DescriptiveStatistics ds = new DescriptiveStatistics();
		
		try {
			File file = new File(filename);
			File absolute = file.getAbsoluteFile();
			absolute.getParentFile().mkdirs();
			FileWriter writer = new FileWriter(absolute, true);
			
			CSVWriter csvWriter = new CSVWriter(writer);
			header[0] = "Distances";
			for(int i = 1; i<header.length;i++){
				header[i] = files.get(i-1).getName().replaceAll("§", "/");
			}
			csvWriter.writeNext(header);
			
			List<String[]> csvRows = new ArrayList<String[]>();
			
			for(int i = 0; i<map.size(); i++){
				String[] row = new String[distanceMatrix[i].length+1];
				row[0] = files.get(i).getName().replaceAll("§", "/");
				for(int j=0;j<distanceMatrix[i].length; j++){
					int val = distanceMatrix[i][j];
					row[j+1] = val+"";
					ds.addValue(val);
				}
				csvRows.add(row);
			}
			csvWriter.writeAll(csvRows);
			csvWriter.flush();
			csvWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
//		System.out.println(ds.getMax() +" Tree depth");
//		System.out.println(ds.getMean() +" Mean tree depth");
		
		MAX_TREE_DISTANCE = ds.getMax();
		AVG_TREE_DISTANCE = ds.getMean();
	}

	private static boolean sameContainment(File file, File file2) {
		if(file.getParent() == null){
			if(file2.getParent() == null)
				return true;
		}
		else
			return file.getParent().equals(file2.getParent());
		
		return false;
	}

//	private static boolean hasEmptyStory(File file) {
//		return false;
//	}
	
}
