package org.springframework.batch.execution.step;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobInterruptedException;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepListener;
import org.springframework.batch.core.listener.StepListenerSupport;
import org.springframework.batch.core.tasklet.Tasklet;
import org.springframework.batch.execution.job.JobSupport;
import org.springframework.batch.execution.step.support.JobRepositorySupport;
import org.springframework.batch.io.exception.InfrastructureException;
import org.springframework.batch.repeat.ExitStatus;

public class TaskletStepTests extends TestCase {

	private StepExecution stepExecution;

	private List list = new ArrayList();

	protected void setUp() throws Exception {
		stepExecution = new StepExecution(new StepSupport("stepName"), new JobExecution(new JobInstance(new Long(0L),
				new JobParameters(), new JobSupport("testJob")), new Long(12)));
	}

	public void testTaskletMandatory() throws Exception {
		TaskletStep step = new TaskletStep();
		step.setJobRepository(new JobRepositorySupport());
		try {
			step.afterPropertiesSet();
		}
		catch (IllegalArgumentException e) {
			String message = e.getMessage();
			assertTrue("Message should contain 'tasklet': " + message, message.toLowerCase().contains("tasklet"));
		}
	}

	public void testRepositoryMandatory() throws Exception {
		TaskletStep step = new TaskletStep();
		try {
			step.afterPropertiesSet();
		}
		catch (IllegalArgumentException e) {
			String message = e.getMessage();
			assertTrue("Message should contain 'tasklet': " + message, message.toLowerCase().contains("tasklet"));
		}
	}

	public void testSuccessfulExecution() throws Exception {
		TaskletStep step = new TaskletStep(new StubTasklet(false, false), new JobRepositorySupport());
		step.execute(stepExecution);
		assertNotNull(stepExecution.getStartTime());
		assertEquals(ExitStatus.FINISHED, stepExecution.getExitStatus());
		assertNotNull(stepExecution.getEndTime());
	}

	public void testSuccessfulExecutionWithStepContext() throws Exception {
		TaskletStep step = new TaskletStep(new StubTasklet(false, false, true), new JobRepositorySupport());
		step.afterPropertiesSet();
		step.execute(stepExecution);
		assertNotNull(stepExecution.getStartTime());
		assertEquals(ExitStatus.FINISHED, stepExecution.getExitStatus());
		assertNotNull(stepExecution.getEndTime());
	}

	public void testSuccessfulExecutionWithExecutionContext() throws Exception {
		TaskletStep step = new TaskletStep(new StubTasklet(false, false), new JobRepositorySupport() {
			public void saveOrUpdateExecutionContext(StepExecution stepExecution) {
				list.add(stepExecution);
			}
		});
		step.execute(stepExecution);
		assertEquals(1, list.size());
	}

	public void testSuccessfulExecutionWithFailureOnSaveOfExecutionContext() throws Exception {
		TaskletStep step = new TaskletStep(new StubTasklet(false, false, true), new JobRepositorySupport() {
			public void saveOrUpdateExecutionContext(StepExecution stepExecution) {
				throw new RuntimeException("foo");
			}
		});
		step.afterPropertiesSet();
		try {
			step.execute(stepExecution);
			fail("Expected BatchCriticalException");
		}
		catch (InfrastructureException e) {
			assertEquals("foo", e.getCause().getMessage());
		}
		assertEquals(BatchStatus.UNKNOWN, stepExecution.getStatus());
	}

	public void testFailureExecution() throws Exception {
		TaskletStep step = new TaskletStep(new StubTasklet(true, false), new JobRepositorySupport());
		step.execute(stepExecution);
		assertNotNull(stepExecution.getStartTime());
		assertEquals(ExitStatus.FAILED, stepExecution.getExitStatus());
		assertNotNull(stepExecution.getEndTime());
	}

	public void testSuccessfulExecutionWithListener() throws Exception {
		TaskletStep step = new TaskletStep(new StubTasklet(false, false), new JobRepositorySupport());
		step.setStepListeners(new StepListener[] { new StepListenerSupport() {
			public void beforeStep(StepExecution context) {
				list.add("open");
			}

			public ExitStatus afterStep(StepExecution stepExecution) {
				list.add("close");
				return ExitStatus.CONTINUABLE;
			}
		} });
		step.execute(stepExecution);
		assertEquals(2, list.size());
	}

	public void testExceptionExecution() throws JobInterruptedException, InfrastructureException {
		TaskletStep step = new TaskletStep(new StubTasklet(false, true), new JobRepositorySupport());
		try {
			step.execute(stepExecution);
			fail();
		}
		catch (RuntimeException e) {
			assertNotNull(stepExecution.getStartTime());
			assertEquals(ExitStatus.FAILED, stepExecution.getExitStatus());
			assertNotNull(stepExecution.getEndTime());
		}
	}

	private class StubTasklet extends StepListenerSupport implements Tasklet {

		private final boolean exitFailure;

		private final boolean throwException;

		private final boolean assertStepContext;

		private StepExecution stepExecution;

		public StubTasklet(boolean exitFailure, boolean throwException) {
			this(exitFailure, throwException, false);
		}

		public StubTasklet(boolean exitFailure, boolean throwException, boolean assertStepContext) {
			this.exitFailure = exitFailure;
			this.throwException = throwException;
			this.assertStepContext = assertStepContext;
		}

		public ExitStatus execute() throws Exception {
			if (throwException) {
				throw new Exception();
			}

			if (exitFailure) {
				return ExitStatus.FAILED;
			}

			if (assertStepContext) {
				assertNotNull(this.stepExecution);
			}

			return ExitStatus.FINISHED;
		}

		public void beforeStep(StepExecution stepExecution) {
			this.stepExecution = stepExecution;
		}

	}

}
