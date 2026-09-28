package com.emanstagram.common;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers side effects (WebSocket pushes, storage deletes) until the database
 * transaction has committed, so a client is never told about a row that a
 * rollback then erases.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
