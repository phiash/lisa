package it.unive.lisa;

import it.unive.lisa.LiSAConfiguration.GraphType;
import it.unive.lisa.analysis.AbstractState;
import it.unive.lisa.analysis.AnalysisState;
import it.unive.lisa.analysis.CFGWithAnalysisResults;
import it.unive.lisa.analysis.heap.HeapDomain;
import it.unive.lisa.analysis.symbols.SymbolAliasing;
import it.unive.lisa.analysis.value.TypeDomain;
import it.unive.lisa.analysis.value.ValueDomain;
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
import it.unive.lisa.outputs.serializableGraph.SerializableGraph;
import it.unive.lisa.program.Application;
import it.unive.lisa.program.Program;
import it.unive.lisa.program.ProgramValidationException;
import it.unive.lisa.program.SyntheticLocation;
import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.statement.Statement;
import it.unive.lisa.symbolic.value.Skip;
import it.unive.lisa.type.ReferenceType;
import it.unive.lisa.type.Type;
import it.unive.lisa.type.TypeSystem;
import it.unive.lisa.util.collections.workset.WorkingSet;
import it.unive.lisa.util.datastructures.graph.algorithms.FixpointException;
import it.unive.lisa.util.file.FileManager;
import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
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
	 * @param app         the application to analyze
	 * @param fileManager the file manager for the analysis
	 * 
	 * @return the warnings generated by the analysis
	 */
	Collection<Warning> run(Application app, FileManager fileManager) {
		finalizeApp(app);

		Collection<CFG> allCFGs = app.getAllCFGs();

		AtomicBoolean htmlViewer = new AtomicBoolean(false), subnodes = new AtomicBoolean(false);
		if (conf.serializeInputs)
			for (CFG cfg : IterationLogger.iterate(LOG, allCFGs, "Dumping input cfgs", "cfgs")) {
				SerializableGraph graph = cfg.toSerializableGraph();
				String filename = cfg.getDescriptor().getFullSignatureWithParNames() + "_cfg";

				try {
					fileManager.mkJsonFile(filename, writer -> graph.dump(writer));

					dump(fileManager, filename, conf.analysisGraphs, graph, htmlViewer, subnodes);
				} catch (IOException e) {
					LOG.error("Exception while dumping the analysis results on {}",
							cfg.getDescriptor().getFullSignature());
					LOG.error(e);
				}
			}

		CheckTool tool = new CheckTool(conf, fileManager);
		if (!conf.syntacticChecks.isEmpty())
			ChecksExecutor.executeAll(tool, app, conf.syntacticChecks);
		else
			LOG.warn("Skipping syntactic checks execution since none have been provided");

		try {
			callGraph.init(app);
		} catch (CallGraphConstructionException e) {
			LOG.fatal("Exception while building the call graph for the input program", e);
			throw new AnalysisExecutionException("Exception while building the call graph for the input program", e);
		}

		try {
			interproc.init(app, callGraph, conf.openCallPolicy);
		} catch (InterproceduralAnalysisException e) {
			LOG.fatal("Exception while building the interprocedural analysis for the input program", e);
			throw new AnalysisExecutionException(
					"Exception while building the interprocedural analysis for the input program", e);
		}

		if (state != null) {
			analyze(allCFGs, fileManager, htmlViewer, subnodes);
			Map<CFG, Collection<CFGWithAnalysisResults<A, H, V, T>>> results = new IdentityHashMap<>(allCFGs.size());
			for (CFG cfg : allCFGs)
				results.put(cfg, interproc.getAnalysisResultsOf(cfg));

			@SuppressWarnings({ "rawtypes", "unchecked" })
			Collection<SemanticCheck<A, H, V, T>> semanticChecks = (Collection) conf.semanticChecks;
			if (!semanticChecks.isEmpty()) {
				CheckToolWithAnalysisResults<A, H, V, T> tool2 = new CheckToolWithAnalysisResults<>(
						tool,
						results,
						callGraph);
				tool = tool2;
				ChecksExecutor.executeAll(tool2, app, semanticChecks);
			} else
				LOG.warn("Skipping semantic checks execution since none have been provided");
		} else
			LOG.warn("Skipping analysis execution since no abstract sate has been provided");

		return tool.getWarnings();
	}

	@SuppressWarnings("unchecked")
	private void analyze(Collection<CFG> allCFGs, FileManager fileManager, AtomicBoolean htmlViewer,
			AtomicBoolean subnodes) {
		A state = this.state.top();
		TimerLogger.execAction(LOG, "Computing fixpoint over the whole program",
				() -> {
					try {
						interproc.fixpoint(
								new AnalysisState<>(state, new Skip(SyntheticLocation.INSTANCE), new SymbolAliasing()),
								(Class<? extends WorkingSet<Statement>>) conf.fixpointWorkingSet,
								conf.wideningThreshold);
					} catch (FixpointException e) {
						LOG.fatal(FIXPOINT_EXCEPTION_MESSAGE, e);
						throw new AnalysisExecutionException(FIXPOINT_EXCEPTION_MESSAGE, e);
					}
				});

		GraphType type = conf.analysisGraphs;
		if (conf.serializeResults || type != GraphType.NONE) {
			int nfiles = fileManager.createdFiles().size();

			for (CFG cfg : IterationLogger.iterate(LOG, allCFGs, "Dumping analysis results", "cfgs"))
				for (CFGWithAnalysisResults<A, H, V, T> result : interproc.getAnalysisResultsOf(cfg)) {
					SerializableGraph graph = result.toSerializableGraph(
							st -> result.getAnalysisStateAfter(st).representation().toSerializableValue());
					String filename = cfg.getDescriptor().getFullSignatureWithParNames();
					if (result.getId() != null)
						filename += "_" + result.getId().hashCode();

					try {
						if (conf.serializeResults)
							fileManager.mkJsonFile(filename, writer -> graph.dump(writer));

						dump(fileManager, filename, type, graph, htmlViewer, subnodes);
					} catch (IOException e) {
						LOG.error("Exception while dumping the analysis results on {}",
								cfg.getDescriptor().getFullSignature());
						LOG.error(e);
					}
				}

			if (htmlViewer.get() && fileManager.createdFiles().size() != nfiles)
				try {
					// we dumped at least one file: need to copy the
					// javascript files
					fileManager.generateHtmlViewerSupportFiles(subnodes.get());
				} catch (IOException e) {
					LOG.error("Exception while generating supporting files for the html viwer");
					LOG.error(e);
				}
		}
	}

	private static void dump(FileManager fileManager, String filename, GraphType type, SerializableGraph graph,
			AtomicBoolean htmlViewer, AtomicBoolean subnodes) throws IOException {
		switch (type) {
		case DOT:
			fileManager.mkDotFile(filename, writer -> graph.toDot().dump(writer));
			break;
		case GRAPHML:
			fileManager.mkGraphmlFile(filename, writer -> graph.toGraphml(false).dump(writer));
			break;
		case GRAPHML_WITH_SUBNODES:
			fileManager.mkGraphmlFile(filename, writer -> graph.toGraphml(true).dump(writer));
			subnodes.set(true);
			break;
		case HTML:
			fileManager.mkHtmlFile(filename, writer -> graph.toHtml(false, "results").dump(writer));
			htmlViewer.set(true);
			break;
		case HTML_WITH_SUBNODES:
			fileManager.mkHtmlFile(filename, writer -> graph.toHtml(true, "results").dump(writer));
			htmlViewer.set(true);
			subnodes.set(true);
			break;
		case NONE:
			break;
		default:
			throw new AnalysisExecutionException("Unknown graph type: " + type);
		}
	}

	private static void finalizeApp(Application app) {
		for (Program p : app.getPrograms()) {
			TypeSystem types = p.getTypes();
			// make sure the basic types are registered
			types.registerType(types.getBooleanType());
			types.registerType(types.getStringType());
			types.registerType(types.getIntegerType());
			for (Type t : types.getTypes())
				if (types.canBeReferenced(t))
					types.registerType(new ReferenceType(t));

			TimerLogger.execAction(LOG, "Finalizing input program", () -> {
				try {
					p.getFeatures().getProgramValidationLogic().validateAndFinalize(p);
				} catch (ProgramValidationException e) {
					throw new AnalysisExecutionException("Unable to finalize target program", e);
				}
			});
		}
	}
}
