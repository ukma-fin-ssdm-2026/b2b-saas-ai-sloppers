package ua.edu.ukma.community.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Транзакції для операцій, що змінюють кілька таблиць разом.
 * З базою — справжня транзакція PostgreSQL; у профілі {@code inmemory} бази немає, тож код просто виконується.
 */
@Configuration
public class TransactionConfig {

    @Bean
    TransactionOperations transactionOperations(ObjectProvider<PlatformTransactionManager> transactionManager) {
        PlatformTransactionManager manager = transactionManager.getIfAvailable();
        return manager == null ? TransactionOperations.withoutTransaction() : new TransactionTemplate(manager);
    }
}
