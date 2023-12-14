package it.gov.pagopa.gpd.upload.model;

/**
 * The step to retry
 */
public enum RetryStep {
    NONE,  // not executed
    RETRY, // to retry
    DONE,  // done
    ERROR  // skip retry
}
