package com.firesin.xuipanel.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ResultTest {

    @Test
    fun `Success wraps data correctly`() {
        val result: Result<Int, DomainError> = Result.Success(42)
        assertInstanceOf(Result.Success::class.java, result)
        assertEquals(42, (result as Result.Success).data)
    }

    @Test
    fun `Failure wraps error correctly`() {
        val error = DomainError.InvalidCredentials
        val result: Result<Int, DomainError> = Result.Failure(error)
        assertInstanceOf(Result.Failure::class.java, result)
        assertEquals(error, (result as Result.Failure).error)
    }

    @Test
    fun `map transforms Success data`() {
        val result: Result<Int, DomainError> = Result.Success(2)
        val mapped = result.map { it * 10 }
        assertEquals(20, (mapped as Result.Success).data)
    }

    @Test
    fun `map passes through Failure unchanged`() {
        val error = DomainError.Network(RuntimeException("net"))
        val result: Result<Int, DomainError> = Result.Failure(error)
        val mapped = result.map { it * 10 }
        assertEquals(error, (mapped as Result.Failure).error)
    }

    @Test
    fun `onSuccess called for Success`() {
        var called = false
        Result.Success(1).onSuccess { called = true }
        assertEquals(true, called)
    }

    @Test
    fun `onFailure called for Failure`() {
        var called = false
        Result.Failure(DomainError.InvalidCredentials).onFailure { called = true }
        assertEquals(true, called)
    }
}
