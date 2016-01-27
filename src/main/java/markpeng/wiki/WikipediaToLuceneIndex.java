package markpeng.wiki;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.xml.sax.SAXException;

import info.bliki.wiki.dump.IArticleFilter;
import info.bliki.wiki.dump.Siteinfo;
import info.bliki.wiki.dump.WikiArticle;
import info.bliki.wiki.dump.WikiXMLParser;

public class WikipediaToLuceneIndex implements IArticleFilter {

	private int currentId = 0;
	public IndexWriter indexWriter = null;

	public WikipediaToLuceneIndex(String luceneFolderPath) throws IOException {
		Directory indexDir = FSDirectory.open(new File(luceneFolderPath));
		Analyzer analyzer = new Analyzer() {
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

		IndexWriterConfig config = new IndexWriterConfig(Version.LATEST, analyzer);
		indexWriter = new IndexWriter(indexDir, config);
	}

	@Override
	public void process(WikiArticle page, Siteinfo info) throws SAXException {
		// System.out.println("----------------------------------------");
		// System.out.println(page.getTitle());
		// System.out.println("----------------------------------------");
		// System.out.println(page.getText());

		try {
			index(page.getTitle(), page.getText());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void addDoc(String title, String text) throws IOException {
		Document doc = new Document();
		doc.add(new IntField("id", currentId, Field.Store.YES));
		doc.add(new TextField("title", title, Field.Store.YES));
		doc.add(new TextField("text", text, Field.Store.YES));
		indexWriter.addDocument(doc);

		currentId++;
	}

	public void index(String title, String text) throws IOException {
		System.out.println("----------------------------------------");
		System.out.println("Indexing " + title + " (text size: " + text.length() + ") ......");
		addDoc(title, text);
		System.out.println("Current size:" + currentId);
	}

	public static void main(String[] args) throws IOException {
		// java -cp lucene-wikipedia-0.0.1-jar-with-dependencies.jar
		// markpeng.wiki.WikipediaToLuceneIndex
		// enwiki-latest-pages-articles.xml.bz2 "lucene-wiki-index"

		if (args.length != 2) {
			System.err.println("Usage: java -cp lucene-wikipedia-0.0.1-jar-with-dependencies.jar "
					+ "markpeng.wiki.WikipediaToLuceneIndex <path of XML bz2 file> " + "<path of lucene index folder>");
			System.exit(-1);
		}

		String bz2Filename = args[0];
		String luceneFolderPath = args[1];

		WikipediaToLuceneIndex handler = new WikipediaToLuceneIndex(luceneFolderPath);
		try {
			WikiXMLParser wxp = new WikiXMLParser(bz2Filename, handler);
			wxp.parse();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			handler.indexWriter.close();
		}
	}

}
