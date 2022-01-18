package it.unive.lisa.cron.nonInterference;

import it.unive.lisa.AnalysisSetupException;
import it.unive.lisa.AnalysisTestExecutor;
import it.unive.lisa.LiSAConfiguration;
import it.unive.lisa.analysis.CFGWithAnalysisResults;
import it.unive.lisa.analysis.SimpleAbstractState;
import it.unive.lisa.analysis.heap.MonolithicHeap;
import it.unive.lisa.analysis.nonInterference.NonInterference;
import it.unive.lisa.analysis.nonrelational.inference.InferenceSystem;
import it.unive.lisa.checks.semantic.CheckToolWithAnalysisResults;
import it.unive.lisa.checks.semantic.SemanticCheck;
import it.unive.lisa.interprocedural.ContextBasedAnalysis;
import it.unive.lisa.interprocedural.RecursionFreeToken;
import it.unive.lisa.interprocedural.callgraph.RTACallGraph;
import it.unive.lisa.program.CompilationUnit;
import it.unive.lisa.program.Global;
import it.unive.lisa.program.Unit;
import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.edge.Edge;
import it.unive.lisa.program.cfg.statement.Assignment;
import it.unive.lisa.program.cfg.statement.Statement;
import java.util.Collection;
import org.junit.Test;

public class NonInterferenceTest extends AnalysisTestExecutor {

	@Test
	public void testConfidentialityNI() throws AnalysisSetupException {
		LiSAConfiguration conf = new LiSAConfiguration().setDumpAnalysis(true)
				.setAbstractState(
						new SimpleAbstractState<>(new MonolithicHeap(), new InferenceSystem<>(new NonInterference())))
				.addSemanticCheck(new NICheck());
		perform("non-interference/confidentiality", "program.imp", conf);
	}

	@Test
	public void testIntegrityNI() throws AnalysisSetupException {
		LiSAConfiguration conf = new LiSAConfiguration().setDumpAnalysis(true)
				.setAbstractState(
						new SimpleAbstractState<>(new MonolithicHeap(), new InferenceSystem<>(new NonInterference())))
				.addSemanticCheck(new NICheck());
		perform("non-interference/integrity", "program.imp", conf);
	}

	@Test
	public void testDeclassification() throws AnalysisSetupException {
		LiSAConfiguration conf = new LiSAConfiguration().setDumpAnalysis(true)
				.setAbstractState(
						new SimpleAbstractState<>(new MonolithicHeap(), new InferenceSystem<>(new NonInterference())))
				.setCallGraph(new RTACallGraph())
				.setInterproceduralAnalysis(new ContextBasedAnalysis<>(RecursionFreeToken.getSingleton()))
				.addSemanticCheck(new NICheck());
		perform("non-interference/interproc", "program.imp", conf);
	}

	private static class NICheck
			implements SemanticCheck<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
					MonolithicHeap, InferenceSystem<NonInterference>> {

		@Override
		public void beforeExecution(
				CheckToolWithAnalysisResults<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
						MonolithicHeap, InferenceSystem<NonInterference>> tool) {
		}

		@Override
		public void afterExecution(
				CheckToolWithAnalysisResults<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
						MonolithicHeap, InferenceSystem<NonInterference>> tool) {
		}

		@Override
		public boolean visitCompilationUnit(
				CheckToolWithAnalysisResults<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
						MonolithicHeap, InferenceSystem<NonInterference>> tool,
				CompilationUnit unit) {
			return true;
		}

		@Override
		public void visitGlobal(
				CheckToolWithAnalysisResults<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
						MonolithicHeap, InferenceSystem<NonInterference>> tool,
				Unit unit, Global global,
				boolean instance) {
		}

		@Override
		public boolean visit(
				CheckToolWithAnalysisResults<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
						MonolithicHeap, InferenceSystem<NonInterference>> tool,
				CFG graph) {
			return true;
		}

		@Override
		@SuppressWarnings({ "unchecked" })
		public boolean visit(
				CheckToolWithAnalysisResults<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
						MonolithicHeap, InferenceSystem<NonInterference>> tool,
				CFG graph, Statement node) {
			if (!(node instanceof Assignment))
				return true;

			Assignment assign = (Assignment) node;
			Collection<?> results = tool.getResultOf(graph);

			for (Object res : results) {
				CFGWithAnalysisResults<?, ?, ?> result = (CFGWithAnalysisResults<?, ?, ?>) res;
				InferenceSystem<NonInterference> state = (InferenceSystem<NonInterference>) result
						.getAnalysisStateAfter(assign).getState().getValueState();
				InferenceSystem<NonInterference> left = (InferenceSystem<NonInterference>) result
						.getAnalysisStateAfter(assign.getLeft()).getState().getValueState();
				InferenceSystem<NonInterference> right = (InferenceSystem<NonInterference>) result
						.getAnalysisStateAfter(assign.getRight()).getState().getValueState();

				if (left.getInferredValue().isLowConfidentiality() && right.getInferredValue().isHighConfidentiality())
					tool.warnOn(assign,
							"This assignment assigns a HIGH confidentiality value to a LOW confidentiality variable, thus violating non-interference");

				if (left.getInferredValue().isLowConfidentiality() && state.getExecutionState().isHighConfidentiality())
					tool.warnOn(assign,
							"This assignment, located in a HIGH confidentiality block, assigns a LOW confidentiality variable, thus violating non-interference");

				if (left.getInferredValue().isHighIntegrity() && right.getInferredValue().isLowIntegrity())
					tool.warnOn(assign,
							"This assignment assigns a LOW integrity value to a HIGH integrity variable, thus violating non-interference");

				if (left.getInferredValue().isHighIntegrity() && state.getExecutionState().isLowIntegrity())
					tool.warnOn(assign,
							"This assignment, located in a LOW integrity block, assigns a HIGH integrity variable, thus violating non-interference");
			}
			return true;
		}

		@Override
		public boolean visit(
				CheckToolWithAnalysisResults<SimpleAbstractState<MonolithicHeap, InferenceSystem<NonInterference>>,
						MonolithicHeap, InferenceSystem<NonInterference>> tool,
				CFG graph, Edge edge) {
			return true;
		}

	}
}