# Architecture

## Vision

## High-Level Architecture

Mission
│
Mission Engine
│
Observer
│
Goal Evaluator
│
Planner
│
Decision Engine
│
Goal Reasoner
│
Candidate Generator
│
Candidate Filter
│
Action Scorer
│
Executor
│
Browser

--------------------------------

## Component Responsibilities

Mission

Mission Engine

Observer

Goal Evaluator

Planner

Decision Engine

Goal Reasoner

Candidate Generator

Candidate Filter

Action Scorer

Executor

Browser

--------------------------------

## Runtime Flow

Mission Created

↓

Navigate

↓

Observe

↓

Check Goal (Success / Failed if iteration limit reached)

↓

Generate Candidates

↓

Filter Candidates

↓

Score

↓

Execute

↓

Observe Again

↓

Mission Complete

--------------------------------

## Design Principles

Single Responsibility

Open/Closed Principle

Pipeline Pattern

Chain of Responsibility

Factory Pattern

Dependency Injection

--------------------------------

## Why This Architecture?

Traditional Automation

vs

AEGIS

--------------------------------

## Memory Scope

Memory (`ExecutionMemory`, `VisitedStateMemory`, `WorldModel`) is in-run only, not persisted across separate runs.

Two reasons

- Missions get a random UUID per launch today — there's no stable identity to key persisted state on
- Persistence should be designed for a real consumer (LLM reasoner) rather than built speculatively ahead of one

--------------------------------

## Future AI Extensions

Memory

LLM

RL

Knowledge Graph

Planning