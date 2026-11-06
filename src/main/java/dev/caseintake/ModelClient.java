package dev.caseintake;
/** The live LLM adapter should implement this interface. Never embed credentials. */
public interface ModelClient { String extract(String prompt) throws Exception; }
