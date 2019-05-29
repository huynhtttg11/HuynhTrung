package com.sap.psr.vulas.goals;

import com.sap.psr.vulas.shared.enums.GoalType;

public class TestGoal extends AbstractAppGoal {

	public TestGoal() { super(GoalType.TEST); }

	/**
	 * Empty implementation.
	 */
	@Override
	protected void executeTasks() throws Exception {}
}