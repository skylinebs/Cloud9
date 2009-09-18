package edu.umd.cloud9.collection.clue;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

/**
 * <p>
 * Program to uncompress the ClueWeb09 collection from the original distribution
 * WARC files and repack as block compressed <code>SequenceFiles</code>.
 * </p>
 * 
 * <p>
 * The program takes three command-line arguments:
 * </p>
 * 
 * <ul>
 * <li>[base-path] base path of the ClueWeb09 distribution</li>
 * <li>[output-path] output path</li>
 * <li>[part-num] part number (1 through 10)</li>
 * </ul>
 * 
 * <p>
 * Here's a sample invocation:
 * </p>
 * 
 * <pre>
 * hadoop jar cloud9.jar edu.umd.cloud9.collection.clue.RepackClueWarcRecords \
 *  /umd/collections/ClueWeb09 /umd/collections/ClueWeb09.repacked/en.01 1
 * </pre>
 * 
 * 
 * @author Jimmy Lin
 * 
 */
public class RepackClueWarcRecords extends Configured implements Tool {

	private static final Logger sLogger = Logger.getLogger(UncompressClueWarcRecords.class);

	private static enum Records {
		TOTAL, PAGES
	};

	private static class MyMapper extends MapReduceBase implements
			Mapper<LongWritable, ClueWarcRecord, LongWritable, ClueWarcRecord> {

		ClueWarcDocnoMapping mDocnoMapping = new ClueWarcDocnoMapping();

		public void configure(JobConf job) {
			try {
				mDocnoMapping.loadMapping(new Path(job.get("DocnoMappingDataFile")), FileSystem
						.get(job));
			} catch (Exception e) {
				throw new RuntimeException("Error loading docno mapping data file!");
			}
		}

		public void map(LongWritable key, ClueWarcRecord doc,
				OutputCollector<LongWritable, ClueWarcRecord> output, Reporter reporter)
				throws IOException {
			reporter.incrCounter(Records.TOTAL, 1);

			String id = doc.getHeaderMetadataItem("WARC-TREC-ID");

			if (id != null) {
				reporter.incrCounter(Records.PAGES, 1);
				output.collect(key, doc);
			}
		}
	}

	/**
	 * Creates an instance of this tool.
	 */
	public RepackClueWarcRecords() {
	}

	private static int printUsage() {
		System.out.println("usage: [base-path] [output-path] [part-num] [docno-mapping-data-file]");
		ToolRunner.printGenericCommandUsage(System.out);
		return -1;
	}

	/**
	 * Runs this tool.
	 */
	public int run(String[] args) throws Exception {
		if (args.length != 4) {
			printUsage();
			return -1;
		}

		String basePath = args[0];
		String outputPath = args[1];
		int part = Integer.parseInt(args[2]);
		String data = args[3];

		JobConf conf = new JobConf(RepackClueWarcRecords.class);
		conf.setJobName("RepackClueWarcRecords:part" + part);

		// this is the default size
		int blocksize = 1000000;
		conf.setInt("io.seqfile.compress.blocksize", blocksize);
		conf.set("DocnoMappingDataFile", data);

		sLogger.info("Tool name: RepackClueWarcRecords");
		sLogger.info(" - Base path: " + basePath);
		sLogger.info(" - Output path: " + outputPath);
		sLogger.info(" - Segement number: " + part);
		sLogger.info(" - Docno mapping data file: " + data);
		sLogger.info(" - block size: " + blocksize);

		int mapTasks = 10;

		conf.setNumMapTasks(mapTasks);
		conf.setNumReduceTasks(0);

		ClueCollectionPathConstants.addEnglishCollectionPart(conf, basePath, part);

		SequenceFileOutputFormat.setOutputPath(conf, new Path(outputPath));
		SequenceFileOutputFormat.setCompressOutput(conf, true);
		SequenceFileOutputFormat.setOutputCompressionType(conf, SequenceFile.CompressionType.BLOCK);

		conf.setInputFormat(ClueWarcInputFormat.class);
		conf.setOutputFormat(SequenceFileOutputFormat.class);
		conf.setOutputKeyClass(LongWritable.class);
		conf.setOutputValueClass(ClueWarcRecord.class);

		conf.setMapperClass(MyMapper.class);

		// delete the output directory if it exists already
		FileSystem.get(conf).delete(new Path(outputPath), true);

		JobClient.runJob(conf);

		return 0;
	}

	/**
	 * Dispatches command-line arguments to the tool via the
	 * <code>ToolRunner</code>.
	 */
	public static void main(String[] args) throws Exception {
		int res = ToolRunner.run(new Configuration(), new RepackClueWarcRecords(), args);
		System.exit(res);
	}

}