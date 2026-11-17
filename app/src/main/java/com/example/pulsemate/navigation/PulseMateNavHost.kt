package com.example.pulsemate.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.pulsemate.games.maze.screens.MazeScreen
import com.example.pulsemate.games.snake.screens.SnakeScreen
import com.example.pulsemate.games.brick.screens.BrickScreen
import com.example.pulsemate.games.fruit.screens.FruitScreen
import com.example.pulsemate.games.water.screens.WaterScreen
import com.example.pulsemate.games.whack.screens.WhackScreen
import com.example.pulsemate.games.fit.screens.FitScreen
import com.example.pulsemate.games.four.screens.FourScreen
import com.example.pulsemate.games.battle.screens.BattleScreen
import com.example.pulsemate.games.lock.LockScreen
import com.example.pulsemate.games.draw.screens.DrawScreen
import com.example.pulsemate.games.tile.screens.TileScreen
import com.example.pulsemate.games.chase.ChaseScreen
import com.example.pulsemate.games.flip.FlipScreen
import com.example.pulsemate.games.pop.PopScreen
import com.example.pulsemate.games.dig.DigScreen
import com.example.pulsemate.games.reel.ReelScreen
import com.example.pulsemate.games.memory.MemoryScreen
import com.example.pulsemate.games.merge.screens.MergeScreen
import com.example.pulsemate.games.quiz.screens.CategoryScreen
import com.example.pulsemate.games.quiz.screens.HomeScreen
import com.example.pulsemate.games.quiz.screens.QuizScreen
import com.example.pulsemate.games.quiz.screens.ResultsScreen
import com.example.pulsemate.hub.MainMenuScreen
import com.example.pulsemate.remote.LoveSpouseRemoteScreen
import com.example.pulsemate.remote.PrettyLoveRemoteScreen

@Composable
fun PulseMateNavHost() {
    var currentRoute by rememberSaveable { mutableStateOf(Routes.HUB) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var freePlay by rememberSaveable { mutableStateOf(false) }
    var finalScore by rememberSaveable { mutableStateOf(0) }
    var totalQuestions by rememberSaveable { mutableStateOf(10) }

    fun goBack() {
        currentRoute = when (currentRoute) {
            Routes.QUIZ_HOME -> Routes.HUB
            Routes.CATEGORIES -> Routes.QUIZ_HOME
            Routes.QUIZ -> Routes.CATEGORIES
            Routes.RESULTS -> Routes.CATEGORIES
            Routes.REMOTE -> Routes.HUB
            Routes.MERGE -> Routes.HUB
            Routes.MEMORY -> Routes.HUB
            Routes.MAZE -> Routes.HUB
            Routes.SNAKE -> Routes.HUB
            Routes.BRICK -> Routes.HUB
            Routes.FRUIT -> Routes.HUB
            Routes.WATER -> Routes.HUB
            Routes.WHACK -> Routes.HUB
            Routes.FIT -> Routes.HUB
            Routes.FOUR -> Routes.HUB
            Routes.BATTLE -> Routes.HUB
            Routes.LOCK -> Routes.HUB
            Routes.DRAW -> Routes.HUB
            Routes.PRETTY -> Routes.HUB
            Routes.TILE -> Routes.HUB
            Routes.CHASE -> Routes.HUB
            Routes.FLIP -> Routes.HUB
            Routes.POP -> Routes.HUB
            Routes.DIG -> Routes.HUB
            Routes.REEL -> Routes.HUB
            else -> Routes.HUB
        }
    }

    BackHandler(enabled = currentRoute != Routes.HUB) {
        goBack()
    }

    when (currentRoute) {
        Routes.HUB -> {
            MainMenuScreen(
                onQuizClick = {
                    currentRoute = Routes.QUIZ_HOME
                },
                onMergeClick = {
                    currentRoute = Routes.MERGE
                },
                onMemoryClick = {
                    currentRoute = Routes.MEMORY
                },
                onMazeClick = {
                    currentRoute = Routes.MAZE
                },
                onSnakeClick = {
                    currentRoute = Routes.SNAKE
                },
                onBrickClick = {
                    currentRoute = Routes.BRICK
                },
                onFruitClick = {
                    currentRoute = Routes.FRUIT
                },
                onWaterClick = {
                    currentRoute = Routes.WATER
                },
                onWhackClick = {
                    currentRoute = Routes.WHACK
                },
                onFitClick = {
                    currentRoute = Routes.FIT
                },
                onFourClick = {
                    currentRoute = Routes.FOUR
                },
                onBattleClick = {
                    currentRoute = Routes.BATTLE
                },
                onLockClick = {
                    currentRoute = Routes.LOCK
                },
                onDrawClick = {
                    currentRoute = Routes.DRAW
                },
                onTileClick = {
                    currentRoute = Routes.TILE
                },
                onChaseClick = {
                    currentRoute = Routes.CHASE
                },
                onFlipClick = {
                    currentRoute = Routes.FLIP
                },
                onPopClick = {
                    currentRoute = Routes.POP
                },
                onDigClick = {
                    currentRoute = Routes.DIG
                },
                onReelClick = {
                    currentRoute = Routes.REEL
                },
                onPrettyClick = {
                    currentRoute = Routes.PRETTY
                },
                onRemoteClick = {
                    currentRoute = Routes.REMOTE
                }
            )
        }

        Routes.QUIZ_HOME -> {
            HomeScreen(
                onPlay = {
                    currentRoute = Routes.CATEGORIES
                },
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.CATEGORIES -> {
            CategoryScreen(
                freePlay = freePlay,
                onFreePlayChange = { freePlay = it },
                onAllCategories = {
                    selectedCategory = null
                    currentRoute = Routes.QUIZ
                },
                onCategorySelected = { category ->
                    selectedCategory = category
                    currentRoute = Routes.QUIZ
                },
                onBack = {
                    currentRoute = Routes.QUIZ_HOME
                }
            )
        }

        Routes.QUIZ -> {
            QuizScreen(
                category = selectedCategory,
                freePlay = freePlay,
                onBack = {
                    currentRoute = Routes.CATEGORIES
                },
                onFinished = { score, total ->
                    finalScore = score
                    totalQuestions = total
                    currentRoute = Routes.RESULTS
                }
            )
        }

        Routes.RESULTS -> {
            ResultsScreen(
                score = finalScore,
                totalQuestions = totalQuestions,
                onPlayAgain = {
                    currentRoute = Routes.QUIZ
                },
                onCategories = {
                    currentRoute = Routes.CATEGORIES
                },
                onHome = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.MERGE -> {
            MergeScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.MEMORY -> {
            MemoryScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.MAZE -> {
            MazeScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.SNAKE -> {
            SnakeScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.BRICK -> {
            BrickScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.FRUIT -> {
            FruitScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.WATER -> {
            WaterScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.WHACK -> {
            WhackScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.FIT -> {
            FitScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.FOUR -> {
            FourScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.BATTLE -> {
            BattleScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.LOCK -> {
            LockScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.DRAW -> {
            DrawScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.TILE -> {
            TileScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.CHASE -> {
            ChaseScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.FLIP -> {
            FlipScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.POP -> {
            PopScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.DIG -> {
            DigScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.REEL -> {
            ReelScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.PRETTY -> {
            PrettyLoveRemoteScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        Routes.REMOTE -> {
            LoveSpouseRemoteScreen(
                onBack = {
                    currentRoute = Routes.HUB
                }
            )
        }

        else -> {
            currentRoute = Routes.HUB
        }
    }
}