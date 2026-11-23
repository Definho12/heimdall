package com.guilherme.heimdall

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Verificação em segundo plano, de tempos em tempos. */
class Trabalho(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val r = Verificador.verificar(applicationContext, emPrimeiroPlano = false)
        return if (r.erro != null && r.novos == 0) Result.retry() else Result.success()
    }

    companion object {
        fun agendar(ctx: Context, mudou: Boolean = false) {
            val horas = Armazem(ctx).horas.toLong()
            val pedido = PeriodicWorkRequestBuilder<Trabalho>(horas, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(horas, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("verificar",
                    if (mudou) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, pedido)
        }
    }
}
