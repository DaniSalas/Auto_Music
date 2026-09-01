package com.danielsalas.auto_music.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danielsalas.auto_music.AppTranslations
import com.danielsalas.auto_music.sync.ListenTogetherManager

@Composable
fun ListenTogetherScreen(
    strings: AppTranslations,
    manager: ListenTogetherManager?
) {
    var roomCode by remember { mutableStateOf("") }
    var currentRoom by remember { mutableStateOf<String?>(null) }
    var isHost by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = strings.roomTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = strings.roomNote,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.secondary
        )
        
        Spacer(Modifier.height(32.dp))

        if (currentRoom == null) {
            // Join Room Section
            OutlinedTextField(
                value = roomCode,
                onValueChange = { roomCode = it.uppercase() },
                label = { Text(strings.roomCodeLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(Modifier.height(16.dp))
            
            Button(
                onClick = {
                    isLoading = true
                    manager?.joinRoom(roomCode) { success ->
                        isLoading = false
                        if (success) {
                            currentRoom = roomCode
                            isHost = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = roomCode.length >= 4 && !isLoading
            ) {
                if (isLoading && !isHost) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text(strings.roomJoin)
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(text = strings.orSeparator, style = MaterialTheme.typography.labelLarge)
            
            Spacer(Modifier.height(16.dp))
            
            // Create Room Section
            ElevatedButton(
                onClick = {
                    isLoading = true
                    manager?.createRoom { code ->
                        isLoading = false
                        if (code != null) {
                            currentRoom = code
                            isHost = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading && isHost) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text(strings.roomCreate)
            }
        } else {
            // Active Room Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = strings.roomCodeLabel, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = currentRoom!!,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = strings.roomOwnerToggle, 
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isHost,
                            onCheckedChange = { 
                                isHost = it
                                manager?.setAsOwner(it)
                            }
                        )
                    }
                    
                    if (isHost) {
                        Text(
                            text = strings.roomOwnerLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            OutlinedButton(
                onClick = {
                    manager?.leaveRoom()
                    currentRoom = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.roomLeave)
            }
        }
    }
}
