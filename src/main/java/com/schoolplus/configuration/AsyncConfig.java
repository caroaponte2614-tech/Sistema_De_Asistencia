package com.schoolplus.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Trabajo que no debe colgar del hilo de la petición (§5.3).
 *
 * <p>De momento lo usa el aviso por correo del flujo de aprobaciones: quien aprueba no
 * tiene por qué esperar a que Gmail conteste. Los reportes en Excel del §4.8 usarán este
 * mismo ejecutor cuando lleguen.</p>
 *
 * <p>La cola está acotada a propósito. Con una cola infinita, un SMTP caído acumularía
 * tareas hasta agotar la memoria; con tope, la política de descarte deja que el hilo que
 * llama ejecute la tarea él mismo, lo que frena la producción en vez de reventar.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("taskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(5);
        ejecutor.setQueueCapacity(100);
        ejecutor.setThreadNamePrefix("school-plus-async-");
        ejecutor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        // Que el apagado espere a que salgan los correos en vuelo.
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(20);
        ejecutor.initialize();
        return ejecutor;
    }
}
