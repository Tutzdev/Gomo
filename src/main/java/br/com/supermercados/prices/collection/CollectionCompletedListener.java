package br.com.supermercados.prices.collection;

/** Runs after a batch of collectors finishes, whatever their individual outcome. */
public interface CollectionCompletedListener {

    void collectionCompleted();
}
