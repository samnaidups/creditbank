package co.uk.cordacodeclub.flows

import co.paralleluniverse.fibers.Suspendable
import co.uk.cordacodeclub.contract.RequestContract
import co.uk.cordacodeclub.state.RequestState
import co.uk.cordacodeclub.state.StatementState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap


@StartableByRPC
class RequestStatementFlow(val nino: String) : FlowLogic<Unit>() {
    // TODO
    // NINO VALIDATION FUNCITON, ->FLOW EXCEPTION
    // BROADCAST REQUEST

    // CREATE RESPONDER CLASS ANNOTATION -> look in crowdfunding example


    // CROWDFUNDING EXAMPLE
    // ASK NWM FOR NODES
    // CREATE FLOW SESSION FOR EACH NODE
    // SEND REQUEST to EACH NODE

    // RESPONDER FLOW
    //      CHECK IF RECEIVED IS PRESENT
//                YES -> START SUBFLOW SEND STATEMENT FLOW
    //  TO RECIEVE...


    @Suspendable
    override fun call(): Unit {
//        val reqState = RequestState.issue(nino)

//        val txBuilder = createTransactionBuilder(reqState)

//        val fullySignedTransaction = serviceHub.signInitialTransaction(txBuilder)

        // Send the request transaction to all the remaining parties.

        subFlow(BroadcastTransaction(nino))
    }
    @Suspendable
    private fun createTransactionBuilder(requestState: RequestState): TransactionBuilder {
        var txBuilder = TransactionBuilder(getNotary())

        //For each found input Statement state add requestor as Participant
        txBuilder.addOutputState(requestState, RequestContract.STATEMENT_CONTRACT_ID)
        return txBuilder
    }

    @Suspendable
    private fun getNotary(): Party {
        val notary = serviceHub.networkMapCache.notaryIdentities.first();
        return notary
    }
}

// From observable-states cordapp example
@InitiatingFlow
class BroadcastTransaction(val nino: String) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        // Get a list of all identities from the network map cache.
        val everyone = serviceHub.networkMapCache.allNodes.flatMap { it.legalIdentities }

        // Filter out the notary identities and remove our identity.
        val everyoneButMeAndNotary = everyone.filter { serviceHub.networkMapCache.isNotary(it).not() } - ourIdentity

        // Create a session for each remaining party.
        val sessions = everyoneButMeAndNotary.map { initiateFlow(it) }

        // Send the transaction to all the remaining parties.
        sessions.forEach {
            it.send(nino)
        }
    }
}

@InitiatedBy(BroadcastTransaction::class)
class RequestResponderFlow(val counterPartySession : FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val nino:String  = counterPartySession.receive(String::class.java).unwrap({it})
        if (checkForStatementInVaultForRequestor(nino, counterPartySession.counterparty))
            subFlow(SendStatementFlow(nino, ourIdentity))

    }

    @Suspendable
    private fun checkForStatementInVaultForRequestor(nino:String, counterParty : Party): Boolean {
        return !findStatementsByNinoAndRequestor(nino,counterParty).isEmpty()
    }

    @Suspendable
    private fun findStatementsByNinoAndRequestor(nino:String, requestor:Party): List<StateAndRef<StatementState>> {
        val allStatementStates = serviceHub.vaultService.queryBy(StatementState::class.java).states
        val filteredStatementStates = allStatementStates.filter {
            it.state.data.nino.equals(nino) && !it.state.data
                    .participants.contains(requestor)
        }
        return filteredStatementStates
    }
}
