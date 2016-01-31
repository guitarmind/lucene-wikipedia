package markpeng.wiki;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.util.StringTokenizer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;

public class QuestionToWiki {

	private String inputPath;
	private String outputPath;

	private Analyzer analyzer;
	private IndexSearcher searcher;
	private IndexReader luceneReader;

	public QuestionToWiki(String luceneFolderPath, String inputPath, String outputPath) {
		this.inputPath = inputPath;
		this.outputPath = outputPath;

		try {
			analyzer = new Analyzer() {
				@Override
				protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
					StandardTokenizer tokenzier = new StandardTokenizer(reader);
					TokenStream ts = new StopFilter(tokenzier, StandardAnalyzer.STOP_WORDS_SET);
					int flags = WordDelimiterFilter.SPLIT_ON_NUMERICS | WordDelimiterFilter.SPLIT_ON_CASE_CHANGE
							| WordDelimiterFilter.GENERATE_NUMBER_PARTS | WordDelimiterFilter.GENERATE_WORD_PARTS;
					ts = new WordDelimiterFilter(ts, flags, null);
					ts = new LowerCaseFilter(ts);
					ts = new PorterStemFilter(ts);

					return new TokenStreamComponents(tokenzier, ts);
				}
			};

			luceneReader = DirectoryReader.open(FSDirectory.open(new File(luceneFolderPath)));
			searcher = new IndexSearcher(luceneReader);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void questionAnswering(int topN) throws Exception {
		BufferedReader inputReader = null;
		BufferedWriter outputWriter = null;

		try {
			inputReader = new BufferedReader(new InputStreamReader(new FileInputStream(inputPath)));


			File outputFileTest = new File(outputPath);
			String prevId = null;
			if (outputFileTest.exists()) {
				prevId = readPreviousCompletedId();
				System.out.println("Last completed Id: " + prevId + "\n\n");
				
				outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath, true)));
			} else {
				outputWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath, false)));
				// write file header
				outputWriter.write("id,correctAnswer");
				outputWriter.newLine();
				outputWriter.flush();
			}

			String aLine;
			// skip first line
			inputReader.readLine();

			boolean startQuery = false;
			if (prevId == null)
				startQuery = true;
			while ((aLine = inputReader.readLine()) != null) {
				if (startQuery) {
					StringTokenizer tk = new StringTokenizer(aLine, "\t");
					if (tk.countTokens() == 6) {
						String id = tk.nextToken();
						String question = escapeSymbols(tk.nextToken());

						System.out.println("\n\nQuery id=" + id + " ===> " + question);

						double maxScore = 0.0;
						int finalAns = -1;
						// get score from 4 queries
						for (int i = 0; i < 4; i++) {
							int answerId = (i + 1);

							String qstring = question;
							String ans = escapeSymbols(tk.nextToken());
							qstring = createQueryString(qstring, ans);

							QueryParser parser = new QueryParser("text", analyzer);
							Query query = parser.parse("title:(" + qstring + ") OR text:(" + qstring + ")");
							System.out.println("Ans " + answerId + ":  " + ans);

							// get top hits
							TopScoreDocCollector collector = TopScoreDocCollector.create(topN, true);
							searcher.search(query, collector);
							ScoreDoc[] hits = collector.topDocs().scoreDocs;

							System.out.println("Found : " + hits.length + " hits.");
							double sumScore = 0.0;
							for (int j = 0; j < hits.length; j++) {
								int docId = hits[j].doc;
								Document d = searcher.doc(docId);
								String title = d.get("title");
								String text = d.get("text");
								double score = hits[j].score;
								System.out.println((j + 1) + ": title=" + title + ", score=" + score);

								sumScore += score;
							}

							if (sumScore > maxScore) {
								maxScore = sumScore;
								finalAns = answerId;
							}
						} // end of for

						if (finalAns > 0) {
							String ansStr = "";
							if (finalAns == 1)
								ansStr = "A";
							else if (finalAns == 2)
								ansStr = "B";
							else if (finalAns == 3)
								ansStr = "C";
							else if (finalAns == 4)
								ansStr = "D";

							outputWriter.write(id);
							outputWriter.write(",");
							outputWriter.write(ansStr);
							outputWriter.newLine();

							outputWriter.flush();
						}
					}
				}

				if (prevId != null && aLine.startsWith(prevId))
					startQuery = true;

			} // end of while

		} finally {
			if (luceneReader != null)
				luceneReader.close();

			if (inputReader != null)
				inputReader.close();

			if (outputWriter != null) {
				outputWriter.flush();
				outputWriter.close();
			}

		}
	}

	private String createQueryString(String qstring, String ans) {
		if (qstring.contains("__________"))
			qstring = qstring.replace("__________", " " + ans + " ");
		else {
			qstring = qstring.replace("?", " ");
			qstring = qstring + " " + ans;
		}

		return qstring;
	}

	private String escapeSymbols(String origin) {
		// escape + - && || ! ( ) { } [ ] ^ " ~ * ? : \
		return origin.replace("(", " ").replace(")", " ").replace("+", " ").replace("-", " ").replace("&", " ")
				.replace("|", " ").replace("{", " ").replace("}", " ").replace("[", " ").replace("]", " ")
				.replace("^", " ").replace("\"", " ").replace("~", " ").replace("*", " ").replace("?", " ")
				.replace(":", " ").replace("\\", " ").replace("/", " ");
	}

	private String readPreviousCompletedId() {
		String prevId = null;

		String aLine;
		try {
			BufferedReader inputReader = new BufferedReader(new InputStreamReader(new FileInputStream(outputPath)));

			// read until last line
			while ((aLine = inputReader.readLine()) != null) {
				prevId = aLine.split(",")[0];
			}

			inputReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return prevId;
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			// "java -cp lucene-wikipedia-0.0.1-jar-with-dependencies.jar
			// markpeng.wiki.QuestionToWiki /home/uitox/wiki/lucene-wiki-index
			// validation_set.tsv lucene_top1_or_submit.csv"
			System.err.println("Usage: java -cp lucene-wikipedia-0.0.1-jar-with-dependencies.jar "
					+ "markpeng.wiki.QuestionToWiki <path of lucene index folder> " + " <input file> "
					+ "<output file> <topN>");
			System.exit(-1);
		}

		String luceneFolderPath = args[0];
		String inputPath = args[1];
		String outputPath = args[2];
		int topN = Integer.parseInt(args[3]);

		QuestionToWiki worker = new QuestionToWiki(luceneFolderPath, inputPath, outputPath);
		// worker.questionAnswering(1);
		worker.questionAnswering(topN);
	}

}
