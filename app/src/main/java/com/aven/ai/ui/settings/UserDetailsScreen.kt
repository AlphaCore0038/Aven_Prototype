package com.aven.ai.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aven.ai.data.settings.UserProfileRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DATE_FORMAT = "dd/MM/yyyy"

/**
 * Edit the user's stored details (name / date of birth / email). Every change
 * is saved immediately; the name is displayed in the chat welcome greeting.
 *
 * Each field keeps local state so typing stays instant (no round-trip through
 * the DataStore flow per keystroke); [LaunchedEffect] re-syncs local state if
 * the stored value changes elsewhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailsScreen(
    userProfileRepository: UserProfileRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile by userProfileRepository.profile.collectAsStateWithLifecycle(
        initialValue = UserProfileRepository.UserProfile()
    )
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(profile.name) }
    var dateOfBirth by remember { mutableStateOf(profile.dateOfBirth) }
    var email by remember { mutableStateOf(profile.email) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(profile.name) { name = profile.name }
    LaunchedEffect(profile.dateOfBirth) { dateOfBirth = profile.dateOfBirth }
    LaunchedEffect(profile.email) { email = profile.email }

    val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT)
    val initialPickerDate = if (profile.dateOfBirth.isNotBlank())
        LocalDate.parse(profile.dateOfBirth, dateFormatter)
    else LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "User Details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "These details stay on this device. Your name appears in the chat greeting.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { newName ->
                name = newName
                scope.launch { userProfileRepository.setName(newName) }
            },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        )

        // Read-only Date of Birth field that opens DatePickerDialog on click
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clickable { showDatePicker = true }
        ) {
            OutlinedTextField(
                value = dateOfBirth,
                onValueChange = { }, // read-only
                label = { Text("Date of Birth") },
                placeholder = { Text(DATE_FORMAT) },
                singleLine = true,
                enabled = false,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.CalendarMonth,
                        contentDescription = "Select date",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { newEmail ->
                email = newEmail
                scope.launch { userProfileRepository.setEmail(newEmail) }
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        )
    }

    if (showDatePicker) {
        val minDate = LocalDate.of(1920, 1, 1)
        val maxDate = LocalDate.now()
        val currentPickerDate = if (dateOfBirth.isNotBlank())
            LocalDate.parse(dateOfBirth, dateFormatter)
        else initialPickerDate

        val pickerState = remember { DatePickerState(
            locale = Locale.getDefault(),
            initialSelectedDate = currentPickerDate,
            initialDisplayedMonth = YearMonth.from(currentPickerDate)
        ) }

        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Select Date of Birth") },
            text = {
                DatePicker(state = pickerState)
            },
            confirmButton = {
                Text(
                    "OK",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            pickerState.selectedDateMillis?.let { millis ->
                                val date = LocalDate.ofEpochDay(millis / 86400000)
                                val formatted = date.format(dateFormatter)
                                dateOfBirth = formatted
                                scope.launch { userProfileRepository.setDateOfBirth(formatted) }
                            }
                            showDatePicker = false
                        }
                )
            },
            dismissButton = {
                Text(
                    "Cancel",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = false }
                )
            }
        )
    }
}