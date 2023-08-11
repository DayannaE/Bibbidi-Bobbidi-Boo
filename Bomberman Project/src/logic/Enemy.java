package logic;

import java.util.Random;

/**
 * Specialization of Character whose movement is controlled by the system based on player actions.
 * If it collides with a player without a Power Pellet, the player loses a life.
 */
public abstract class Enemy extends Character {
    private static final int SCORE_PER_ENEMY = 200;
    private final int FLEE_SPEED = 1;
    private final int NORMAL_SPEED;
    private final int MOVE_VALUE = 1;
    private Key previousDirection;
    private Player player;
    private boolean fleeing = false;
    private int spawnColumn, spawnRow;
    private boolean reachedCorner;

    public Enemy(int speed, Map map, Player player, int waitTime) {
        this.speed = speed;
        NORMAL_SPEED = speed;
        this.map = map;
        this.imageDirection = "up";
        this.direction = null;
        this.previousDirection = null;
        this.reachedCorner = false;
        this.player = player;
        this.waitTime = player.waitTime + calculateTime(waitTime);
    }

    public abstract void moveByPersonality();

    @Override
    public void update() {
        if (isPaused() || !canContinueGame()) {
            return;
        }

        checkPlayerState();

        if (isWaiting) {
            actionWhenWaiting(waitTime);
        } else if (fleeing) {
            flee();
        } else if (isCollidingWithPlayer()) {
            player.reappear();
        } else if (!reachedCorner) {
            correctPosition();
            moveToCorner();
        } else {
            correctPosition();
            changeSpeed();
            moveByPersonality();
        }
    }

    private void checkPlayerState() {
        if (!player.isAlive) {
            resetEnemyState();
        } else if (player.activatedPowerPellet()) {
            fleeing = true;
        } else if (!player.hasPowerPellet()) {
            fleeing = false;
        }
    }

    private void resetEnemyState() {
        fleeing = false;
        isWaiting = true;
        counter = 0;
        reachedCorner = false;
    }

    public boolean isPaused() {
        return player.paused;
    }

    private boolean canContinueGame() {
        if (player.getLives() == 0 || player.ateAllPacDots()) {
            thread = false;
            return false;
        }
        return true;
    }

    protected boolean isCollidingWithPlayer() {
        int maxCollisionDistance = 12; // Adjust this value as needed
        int xDistance = Math.abs(this.positionX - player.positionX);
        int yDistance = Math.abs(this.positionY - player.positionY);
        return xDistance <= maxCollisionDistance && yDistance <= maxCollisionDistance;
    }

    /**
     * Defines the behavior of the ghost in its spawn while the game hasn't started yet
     */
    public void actionWhenWaiting(int waitTime) {
        if (counter == 0) {
            this.positionX = map.getEnemySpawnCol();
            this.positionY = map.getPlayerSpawnRow();
        }
        moveRandomly();
        if (isTimeUp(waitTime)) {
            isWaiting= false;
            this.positionX = map.getEnemySpawnCol();
            this.positionY = map.getEnemySpawnRow();
        }
    }

    public void moveRandomly() {
        changeSpeed();
        if (isAligned()) {
            previousDirection = direction;
            direction = getRandomValidDirection(previousDirection);
        }
        move(direction, speed);
    }

    public void changeSpeed(){
        if(hasFleeSpeed()){
            speed = NORMAL_SPEED;
        }
    }

    private void moveToCorner() {
        changeSpeed();
        if (!canTeleport()) {
            if (isAligned()) {
                if (getColumnMap() == spawnColumn && getRowMap() == spawnRow) {
                    this.reachedCorner = true;
                }
                previousDirection = direction;
                direction = getBestDirection(spawnColumn, spawnRow);
            }
        }
        move(direction, speed);
    }

    /**
     * Uses flee speed to move the ghost until its position is a multiple of its original speed.
     * This keeps the character aligned despite several changes in speed.
     */
    private void correctPosition() {
        while (positionX % speed != 0 || positionY % speed != 0) {
            if(!hasFleeSpeed()){
                speed = FLEE_SPEED;
            }
            move(direction, speed);
        }
    }

    public boolean hasFleeSpeed(){
        return speed == FLEE_SPEED;
    }

    @Override
    public boolean canMove(Key key) {
        return super.canMove(key) && !isOppositeDirection(key, previousDirection);
    }

    public void flee() {
        if(!hasFleeSpeed()){
            speed = FLEE_SPEED;
        }
        if (!canTeleport()) {
            if (isAligned()) {
                previousDirection = direction;
                direction = getBestFleeDirection();
            }
        }
        if (isCollidingWithPlayer()) {
            isWaiting = true;
            fleeing = false;
            player.score += SCORE_PER_ENEMY * player.bonusForEnemyKilled;
            player.bonusForEnemyKilled++;
        }
        move(direction, speed);
    }

    /**
     * Returns the movement direction that will take the ghost farthest from the player,
     * considering that it should not be opposite to its current direction, thus preventing it from
     * returning
     *
     * @return movement
     */
    private Key getBestFleeDirection() {
        Key movement = null;
        double maxDistance = 0;
        double tempDistance = calculateDistance(0, -MOVE_VALUE, player.getColumnMap(), player.getRowMap());
        if (canMove(Key.UP)) {
            maxDistance = tempDistance;
            movement = Key.UP;
        }
        tempDistance = calculateDistance(0, MOVE_VALUE, player.getColumnMap(), player.getRowMap());
        if (tempDistance > maxDistance && canMove(Key.DOWN)) {
            maxDistance = tempDistance;
            movement = Key.DOWN;
        }
        tempDistance = calculateDistance(-MOVE_VALUE, 0, player.getColumnMap(), player.getRowMap());
        if (tempDistance > maxDistance && canMove(Key.LEFT)) {
            maxDistance = tempDistance;
            movement = Key.LEFT;
        }
        tempDistance = calculateDistance(MOVE_VALUE, 0, player.getColumnMap(), player.getRowMap());
        if (tempDistance > maxDistance && canMove(Key.RIGHT)) {
            maxDistance = tempDistance;
            movement = Key.RIGHT;
        }
        return movement;
    }

    protected Key getRandomValidDirection(Key currentKey) {
        Random rand = new Random();
        Key key;
        Key[] keyArray = {Key.UP, Key.DOWN, Key.LEFT, Key.RIGHT};
        do {
            key = keyArray[rand.nextInt(4)];
        } while (isOppositeDirection(key, currentKey) || !canMove(key));
        return key;
    }

    /**
     * Returns the movement direction that will take the ghost closest to a specific column and row,
     * considering that it should not be opposite to its current direction
     *
     * @return movement
     */
    public Key getBestDirection(int column, int row) {
        Key movement = null;
        double minDistance = 10000;
        double tempDistance = calculateDistance(0, -MOVE_VALUE, column, row);
        if (canMove(Key.UP)) {
            minDistance = tempDistance;
            movement = Key.UP;
        }
        tempDistance = calculateDistance(0, MOVE_VALUE, column, row);
        if (tempDistance < minDistance && canMove(Key.DOWN)) {
            minDistance = tempDistance;
            movement = Key.DOWN;
        }
        tempDistance = calculateDistance(-MOVE_VALUE, 0, column, row);
        if (tempDistance < minDistance && canMove(Key.LEFT)) {
            minDistance = tempDistance;
            movement = Key.LEFT;
        }
        tempDistance = calculateDistance(MOVE_VALUE, 0, column, row);
        if (tempDistance < minDistance && canMove(Key.RIGHT)) {
            minDistance = tempDistance;
            movement = Key.RIGHT;
        }
        return movement;
    }

    /**
     * Calculates the magnitude of the diagonal from the current position of the ghost to a specific
     * maze coordinate consisting of a column and a row
     *
     * @return
     */
    protected double calculateDistance(int extraColumns, int extraRows, int placeToCompareCol, int placeToCompareRow) {
        return Math.sqrt(Math.pow(getColumnMap() + extraColumns - placeToCompareCol, 2) + Math.pow(getRowMap() + extraRows - placeToCompareRow, 2));
    }

    public boolean isFleeing() {
        return fleeing;
    }
}
