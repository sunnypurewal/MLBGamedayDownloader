package downloader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Document;

public class Downloader {
	
	static String baseURL = "http://gd2.mlb.com/components/game/mlb";
	static String baseLocalURL;
	static ArrayList<String> files = new ArrayList<String>();
	
	public static void main(String[] args) {
		Downloader downloader = new Downloader();
		int year;
		if (args.length < 2) {
			System.out.println("Invalid number of arguments");
			return;
		}
		year = Integer.parseInt(args[0]);
		baseLocalURL = args[1];
		if (args.length > 2) {
			for (int i = 2; i < args.length; i++) {
				files.add(args[i]);
			}
		}
		else {
			System.out.println("No filenames specified, using default: game_events.json, boxscore.json, inning_all.xml, inning_hit.xml, inning_Scores.xml");
			files.add("game_events.json");
			files.add("boxscore.json");
			files.add("inning_Scores.xml");
			files.add("inning_all.xml");
			files.add("inning_hit.xml");
		}
		downloader.download(year);
	}
	
	public void download(int year) {
		for(int month = 4 ; month <= 11 ; month++) {
			download(year, month);
		}
	}
	
	public void download(int year, int month) {
		try {
			for( int day = 1 ; day <= 31 ; day++) {
				String parentFolder = String.format("%s/year_%02d/month_%02d/day_%02d/",
						baseURL,
						year,
						month,
						day);
	
				Document doc = null;
				try {
					doc = Jsoup.connect(parentFolder).post();
				} catch(IOException e) {
					return;
				}
				Document parse = Jsoup.parse(doc.html());
				
				Elements lis = parse.select("li > a");
				String href;
				ArrayList<String> gameFolders = new ArrayList<String>();
				for (Element li : lis) {
					href = li.attr("href");
					if (href.startsWith("gid_")) {
						gameFolders.add(href.replace("/", ""));
					}
				}
				
				URL u;
				File file = null;
				
				for (String gameFolder : gameFolders) {
					String directory = String.format("%s/year_%02d/month_%02d/day_%02d/%s",
							baseURL,
							year,
							month,
							day,
							gameFolder);
					
					for (String filename : files) {
						if (filename.contains("inning_")) {
							u = new URL(String.format("%s/%s/%s", directory, "inning", filename));
						}
						else {
							u = new URL(String.format("%s/%s", directory, filename));
						}
						file = new File(String.format("%s\\%s",baseLocalURL, u.getFile()));
	
						if (file.exists()) {
							System.out.println(filename + " has already been downloaded");
							continue;
						}
						try {
							FileUtils.copyURLToFile(u, file, 10000, 10000);
						} catch(IOException e) {
							System.out.println(filename + " does not exist on server");
							continue;
						}
						System.out.println(String.format("Downloaded to %s", file.getAbsolutePath()));
					}
	
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
