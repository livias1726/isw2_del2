package main.dataset.control;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

import main.dataset.entity.Bug;
import main.dataset.entity.FileMetadata;
import main.utils.CSVManager;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import javafx.util.Pair;

public class DatasetManager {
	
	private final String project;

	//Instantiation
	private static DatasetManager instance = null;

    private DatasetManager(String projectName) {
    	this.project = projectName;
    }

    public static DatasetManager getInstance(String projectName) {
        if(instance == null) {
        	instance = new DatasetManager(projectName);
        }
        return instance;
    }

	/**
	 * Creates the dataset with information about:
	 * 		- Bugs fixed (Jira)
	 * 		- Commits (Git)
	 * 		- Every java file that is and was part of the project
	 *
	 * @return : dataset filename and project releases
	 * */
	public Pair<String, String[]> getDataset(Logger logger) throws GitAPIException, IOException {
		//--------------------------------------------------JIRA--------------------------------------------------------

		JiraManager jira = JiraManager.getInstance(project);
		ReleaseManager relMan = ReleaseManager.getInstance(jira.getProjectVersions()); //Get releases

		/*LOG*/
		StringBuilder stringBuilder = new StringBuilder("Releases: ");
		stringBuilder.append(Arrays.toString(ReleaseManager.getReleaseNames()));
		/*for(String rel: ReleaseManager.getReleaseNames()){
			stringBuilder.append(rel).append(", ");
		}*/
		logger.info(stringBuilder.toString());

		List<Bug> bugs = jira.getFixes(); //Get list of jira fix tickets

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Total number of Jira tickets retrieved: " + bugs.size());
		logger.info(stringBuilder.toString());

		//---------------------------------------------------GIT--------------------------------------------------------

		GitManager git = GitManager.getInstance(project);
		Map<RevCommit, LocalDate> commits = git.getCommits(); //Get list of every commit in the project

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Total number of commits retrieved: " + commits.size());
		logger.info(stringBuilder.toString());

		bugs = git.manageBugCommits(bugs, commits); //Manage list of commits linked to a jira fix ticket

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Project linkage is: " + getBugLinkage(commits.size(), bugs)*100);
		logger.info(stringBuilder.toString());

		bugs = git.removeUnreferencedBugs(bugs); //Must be after the linkage computation

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),
				"Number of bugs referenced: " + bugs.size());
		logger.info(stringBuilder.toString());

		bugs = git.processFixCommitInfo(bugs);

		//-------------------------------------------------RELEASES-----------------------------------------------------

		bugs = relMan.analyzeBugReleases(bugs);

        /*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),"\nBUGS:");
		for(Bug bug: bugs){
			stringBuilder.append("\n\t").append(bug.getTicketKey()).
					append(": {Injected: ").append(bug.getInjectedVer()).
					append(", Opening: ").append(bug.getOpeningVer()).
					append(", Fix: ").append(bug.getFixVer()).
					append("}");
		}
		logger.info(stringBuilder.toString());

		relMan.removeSecondHalfOfReleases(); //Cut the second half of the releases to get reliable input
		Map<String, Map<RevCommit, LocalDate>> cmPerRelease = relMan.matchCommitsAndReleases(commits);

		/*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),"\nCOMMITS PER RELEASE:");
		for(String rel: ReleaseManager.getReleaseNames()){
			stringBuilder.append("\n\t").append(rel).
					append(" -> ").append(cmPerRelease.get(rel).keySet().size());
		}
		logger.info(stringBuilder.toString());

		//--------------------------------------------------FILES-------------------------------------------------------

		DifferenceTreeManager dt = DifferenceTreeManager.getInstance(project, bugs);
		Map<String, List<FileMetadata>> files = dt.analyzeFilesEvolution(cmPerRelease);

		/*LOG*/
		stringBuilder.replace(0, stringBuilder.length(),"\nFILES PER RELEASE:");
		for(String rel: files.keySet()){
			stringBuilder.append("\n\t").append(rel).
					append(" -> ").append(files.get(rel).size());
		}
		logger.info(stringBuilder.toString());

		String dataset = CSVManager.getInstance().getDataset(project, dt.getFiles()); //Create the dataset

		return new Pair<>(dataset, ReleaseManager.getReleaseNames());
	}

	/**
	 * The linkage is the percentage of commits linked to a Jira ticket for a project.
	 * The bugLinkage is related only to those tickets marked as bugs.
	 *
	 * @param numCommits: total number of commits in the repository
	 * @param bugs: list of Bug instances representing the Jira tickets and the commits referencing them
	 *
	 * @return bugLinkage: percentage of bug linkage
	 * */
	private double getBugLinkage(int numCommits, List<Bug> bugs) {
		int counter = 0;

		for(Bug bug: bugs){
			if(bug.getFixCm() != null){
				counter++;
			}

			if(bug.getReferencingCms() != null){
				counter += bug.getReferencingCms().size();
			}
		}

		return (double)counter/numCommits;
	}

}
