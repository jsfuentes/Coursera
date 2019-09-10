import java.util.Set; 
import java.util.HashSet; 
import java.util.ArrayList;

public class TxHandler {

    private UTXOPool pool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        pool = new UTXOPool(utxoPool); 
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        double inputSum = 0;
        double outputSum = 0;
        Set<UTXO> usedUTXO = new HashSet<UTXO>();

        ArrayList<Transaction.Input> inputs = tx.getInputs();
        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        
        //check inputs
        for(int i = 0; i < inputs.size(); ++i) {
            Transaction.Input in = inputs.get(i);
            UTXO curUTXO = new UTXO(in.prevTxHash, in.outputIndex);
            if(usedUTXO.contains(curUTXO)) { //3
                return false;
            }
            usedUTXO.add(curUTXO);
            Transaction.Output prevOut = pool.getTxOutput(curUTXO);
            if(prevOut == null) { //1
                return false;
            }
            byte[] msg = tx.getRawDataToSign(i);
            if(!Crypto.verifySignature(prevOut.address, msg, in.signature)) { //2
                return false;
            }
            inputSum += prevOut.value;
        }

        //check outputs
        for(int i = 0; i < outputs.size(); ++i) {
            Transaction.Output out = outputs.get(i);
            if(out.value < 0) { //4
                return false;
            }
            outputSum += out.value;
        }

        if(outputSum > inputSum) { //5
            return false;
        }
        return true;
    }

    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        ArrayList<Transaction> acceptedT = new ArrayList<Transaction>();
        for(int i = 0; i < possibleTxs.length; ++i) {
            Transaction curT = new Transaction(possibleTxs[i]);
            if(!isValidTx(curT)) {
                continue;
            }

            acceptedT.add(curT);
        }

        //TODO: Add mutually valid checking
        Set<UTXO> consumedUTXOs = new HashSet<UTXO>();
        for(Transaction tx : acceptedT) {
            ArrayList<Transaction.Input> inputs = tx.getInputs();
            ArrayList<Transaction.Output> outputs = tx.getOutputs();

            boolean allUnused = true;
            for(Transaction.Input in : inputs) {
                UTXO usedUTXO = new UTXO(in.prevTxHash, in.outputIndex);
                if(consumedUTXOs.contains(usedUTXO)) {
                    allUnused = false;
                }
                consumedUTXOs.add(usedUTXO);
            }
            if(!allUnused) {
                continue;
            }

            for(Transaction.Input in : inputs) {
                UTXO usedUTXO = new UTXO(in.prevTxHash, in.outputIndex);
                pool.removeUTXO(usedUTXO);
            }

            for(int i = 0; i < outputs.size(); ++i) {
                Transaction.Output out = outputs.get(i);
                UTXO newUTXO = new UTXO(tx.getHash(), i);
                pool.addUTXO(newUTXO, out);
            }
        }

        Transaction[] finalT = new Transaction[acceptedT.size()];
        return acceptedT.toArray(finalT);
    }

}
