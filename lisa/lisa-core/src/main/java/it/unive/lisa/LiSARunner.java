package it.unive.lisa;

import it.unive.lisa.analysis.AbstractState;
import it.unive.lisa.analysis.AnalysisState;
import it.unive.lisa.analysis.CFGWithAnalysisResults;
import it.unive.lisa.analysis.heap.HeapDomain;
import it.unive.lisa.analysis.value.TypeDomain;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.caches.Caches;
import it.unive.lisa.checks.ChecksExecutor;
import it.unive.lisa.checks.semantic.CheckToolWithAnalysisResults;
import it.unive.lisa.checks.semantic.SemanticCheck;
import it.unive.lisa.checks.syntactic.CheckTool;
import it.unive.lisa.checks.warnings.Warning;
import it.unive.lisa.interprocedural.InterproceduralAnalysis;
import it.unive.lisa.interprocedural.InterproceduralAnalysisException;
import it.unive.lisa.interprocedural.callgraph.CallGraph;
import it.unive.lisa.interprocedural.callgraph.CallGraphConstructionException;
import it.unive.lisa.logging.IterationLogger;
import it.unive.lisa.logging.TimerLogger;
import it.unive.lisa.program.Program;
import it.unive.lisa.program.ProgramValidationException;
import it.unive.lisa.program.SyntheticLocation;
import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.statement.Statement;
import it.unive.lisa.symbolic.value.Skip;
import it.unive.lisa.type.ReferenceType;
import it.unive.lisa.type.Type;
import it.unive.lisa.util.collections.externalSet.ExternalSet;
import it.unive.lisa.util.datastructures.graph.algorithms.FixpointException;
import it.unive.lisa.util.file.FileManager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An auxiliary analysis runner for executing LiSA analysis.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 * 
 * @param <A> the type of {@link AbstractState} contained into the analysis
 *                state that will be used in the analysis fixpoint
 * @param <H> the type of {@link HeapDomain} contained into the abstract state
 *                that will be used in the analysis fixpoint
 * @param <V> the type of {@link ValueDomain} contained into the abstract state
 *                that will be used in the analysis fixpoint
 * @param <T> the type of {@link TypeDomain} contained into the abstract state
 *                that will be used in the analysis fixpoint
 */
public class LiSARunner<A extends AbstractState<A, H, V, T>,
		H extends HeapDomain<H>,
		V extends ValueDomain<V>,
		T extends TypeDomain<T>> {

	private static final String FIXPOINT_EXCEPTION_MESSAGE = "Exception during fixpoint computation";

	private static final Logger LOG = LogManager.getLogger(LiSARunner.class);

	private final LiSAConfiguration conf;

	private final InterproceduralAnalysis<A, H, V, T> interproc;

	private final CallGraph callGraph;

	private final A state;

	/**
	 * Builds the runner.
	 * 
	 * @param conf      the configuration of the analysis
	 * @param interproc the interprocedural analysis to use
	 * @param callGraph the call graph to use
	 * @param state     the abstract state to use for the analysis
	 */
	LiSARunner(LiSAConfiguration conf, InterproceduralAnalysis<A, H, V, T> interproc, CallGraph callGraph, A state) {
		this.conf = conf;
		this.interproc = interproc;
		this.callGraph = callGraph;
		this.state = state;
	}

	/**
	 * Executes the runner on the target program.
	 * 
	 * @param program     the program to analyze
	 * @param fileManager the file manager for the analysis
	 * 
	 * @return the warnings generated by the analysis
	 */
	Collection<Warning> run(Program program, FileManager fileManager) {
		finalizeProgram(program);

		Collection<CFG> allCFGs = program.getAllCFGs();

		if (conf.isDumpCFGs())
			for (CFG cfg : IterationLogger.iterate(LOG, allCFGs, "Dumping input CFGs", "cfgs"))
				dumpCFG(fileManager, "", cfg, st -> "", conf.isJsonFile() ? FileType.JSON : FileType.DOT);

		CheckTool tool = new CheckTool();
		if (!conf.getSyntacticChecks().isEmpty())
			ChecksExecutor.executeAll(tool, program, conf.getSyntacticChecks());
		else
			LOG.warn("Skipping syntactic checks execution since none have been provided");

		try {
			callGraph.init(program);
		} catch (CallGraphConstructionException e) {
			LOG.fatal("Exception while building the call graph for the input program", e);
			throw new AnalysisExecutionException("Exception while building the call graph for the input program", e);
		}

		try {
			interproc.init(program, callGraph, conf.getOpenCallPolicy());
		} catch (InterproceduralAnalysisException e) {
			LOG.fatal("Exception while building the interprocedural analysis for the input program", e);
			throw new AnalysisExecutionException(
					"Exception while building the interprocedural analysis for the input program", e);
		}

		if (state != null) {
			analyze(allCFGs, fileManager);
			Map<CFG, Collection<CFGWithAnalysisResults<A, H, V, T>>> results = new IdentityHashMap<>(allCFGs.size());
			for (CFG cfg : allCFGs)
				results.put(cfg, interproc.getAnalysisResultsOf(cfg));

			@SuppressWarnings({ "rawtypes", "unchecked" })
			Collection<SemanticCheck<A, H, V, T>> semanticChecks = (Collection) conf.getSemanticChecks();
			if (!semanticChecks.isEmpty()) {
				CheckToolWithAnalysisResults<A, H, V, T> tool2 = new CheckToolWithAnalysisResults<>(
						tool,
						results,
						callGraph);
				tool = tool2;
				ChecksExecutor.executeAll(tool2, program, semanticChecks);
			} else
				LOG.warn("Skipping semantic checks execution since none have been provided");
		} else
			LOG.warn("Skipping analysis execution since no abstract sate has been provided");

		return tool.getWarnings();
	}

	private void analyze(Collection<CFG> allCFGs, FileManager fileManager) {
		A state = this.state.top();
		TimerLogger.execAction(LOG, "Computing fixpoint over the whole program",
				() -> {
					try {
						interproc.fixpoint(new AnalysisState<>(state, new Skip(SyntheticLocation.INSTANCE)),
								conf.getFixpointWorkingSet(), conf.getWideningThreshold());
					} catch (FixpointException e) {
						LOG.fatal(FIXPOINT_EXCEPTION_MESSAGE, e);
						throw new AnalysisExecutionException(FIXPOINT_EXCEPTION_MESSAGE, e);
					}
				});

		if (conf.isDumpAnalysis() || conf.isDumpTypeInference())
			for (CFG cfg : IterationLogger.iterate(LOG, allCFGs, "Dumping analysis results", "cfgs"))
				for (CFGWithAnalysisResults<A, H, V, T> result : interproc.getAnalysisResultsOf(cfg)) {
					String filename = result.getId() == null ? "" : result.getId().hashCode() + "_";
					if (conf.isDumpTypeInference())
						dumpCFG(fileManager, "typing___" + filename, result,
								st -> result.getAnalysisStateAfter(st).typeRepresentation().toString(), conf.isJsonFile() ? FileType.JSON : FileType.DOT);
					if (conf.isDumpAnalysis())
						dumpCFG(fileManager, "analysis___" + filename, result,
								st -> result.getAnalysisStateAfter(st).representation().toString(), conf.isJsonFile() ? FileType.JSON : FileType.DOT);
				}
	}

	private static void finalizeProgram(Program program) {
		// fill up the types cache by side effect on an external set
		Caches.types().clear();
		ExternalSet<Type> types = Caches.types().mkEmptySet();
		for (Type t : program.getRegisteredTypes()) {
			types.add(t);
			types.add(new ReferenceType(t));
		}
		types = null;

		TimerLogger.execAction(LOG, "Finalizing input program", () -> {
			try {
				program.validateAndFinalize();
			} catch (ProgramValidationException e) {
				throw new AnalysisExecutionException("Unable to finalize target program", e);
			}
		});
	}

	public enum FileType {JSON, DOT}

	private static void dumpCFG(FileManager fileManager, String filePrefix, CFG cfg,
			Function<Statement, String> labelGenerator, FileType type) {
		try {
			if(type == FileType.JSON){
				fileManager.mkJSONFile(filePrefix + cfg.getDescriptor().getFullSignatureWithParNames(),
						writer -> cfg.dumpJSON(writer, labelGenerator::apply));
			} else if (type == FileType.DOT){
				fileManager.mkDotFile(filePrefix + cfg.getDescriptor().getFullSignatureWithParNames(),
						writer -> cfg.dumpDot(writer, labelGenerator::apply));
			}


		} catch (IOException e) {
			LOG.error("Exception while dumping the analysis results on {}", cfg.getDescriptor().getFullSignature());
			LOG.error(e);
		}
	}
}
