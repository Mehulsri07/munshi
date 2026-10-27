package com.opuscube.munshi.api

class ApiError(val status: Int, message: String) : Exception(message)

interface MunshiApi {
    suspend fun brief(): Brief
    suspend fun next(scope: String = "brief", interrupt: Boolean = false): Next
    suspend fun confirm(id: Long): ActionResult
    suspend fun decline(id: Long): ActionResult
    suspend fun converse(text: String): ActionResult
    suspend fun facts(): Facts
    suspend fun propose(khataId: Long): Proposed
    suspend fun shelfReadings(readings: List<Reading>): ShelfResult
    suspend fun ledger(): Ledger
    suspend fun lookup(phone: String): Profile
    suspend fun merchant(): Profile
    suspend fun saveMerchant(p: Profile): Profile
}
