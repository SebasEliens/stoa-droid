package dev.stoa.droid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.stoa.data.Philosopher
import dev.stoa.rag.Block

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StoaApp).container
        setContent {
            MaterialTheme { ChatScreen(viewModel { ChatViewModel { container.pipeline } }) }
        }
    }
}

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val turns by vm.turns.collectAsState()
    val authors by vm.authors.collectAsState()
    val busy by vm.busy.collectAsState()
    var input by rememberSaveable { mutableStateOf("") }
    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Philosopher.entries.forEach { p ->
                    FilterChip(selected = p in authors, onClick = { vm.toggle(p) }, label = { Text(p.displayName) })
                }
            }
            // shortcut: no auto-scroll to the newest turn yet; add once chats get long.
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(turns) { TurnView(it) }
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask the Stoics…") },
                )
                Button(
                    onClick = {
                        vm.ask(input)
                        input = ""
                    },
                    enabled = !busy && input.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text("Ask") }
            }
        }
    }
}

@Composable
private fun TurnView(turn: Turn) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(turn.question, style = MaterialTheme.typography.titleMedium)
        val answer = turn.answer
        if (answer == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Column
        }
        answer.blocks.forEach { block ->
            when (block) {
                is Block.Prose -> Text(block.text)
                is Block.Quote ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("“${block.text}”", fontStyle = FontStyle.Italic)
                            val p = block.passage
                            Text(
                                "${p.philosopher.displayName}, ${p.ref} (tr. ${p.translator})",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
            }
        }
    }
}
