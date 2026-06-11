import androidx.compose.runtime.*
import kotlinx.browser.localStorage

@Composable
fun App() {
    var currentUser by remember { 
        mutableStateOf(localStorage.getItem("worldcup_session")) 
    }
    
    WorldCupTheme {
        if (currentUser == null) {
            AuthScreen(onLoginSuccess = { username ->
                localStorage.setItem("worldcup_session", username)
                currentUser = username
            })
        } else {
            FixtureScreen(
                username = currentUser!!, 
                onLogout = { 
                    localStorage.removeItem("worldcup_session")
                    currentUser = null 
                },
                onUsernameChange = { newUsername ->
                    localStorage.setItem("worldcup_session", newUsername)
                    currentUser = newUsername
                }
            )
        }
    }
}
