import java.util.*;
import java.io.*;

/* Definitions of the words I use:
 * 
 * cell: a cell of the sudoku
 * zone: the generalized idea of a line, column or square
 * value, number: a number from 1 to N that can go in a cell
 * promoting a cell: assigning a definite value to a cell
 * 
 *
 * 
 *
 * A summary of my solution:
 * 
 * First, it turns a given grid into a structure of Cells and Zones to make part
 * of the solving more efficient.
 * 
 * Next, it uses a backtracking approach with a few twists. Instead of using
 * the standard recursive approach to backtracking, my solution keeps a heap of
 * partially solved sudokus and always chooses to solve the one with the least
 * empty cells.
 *
 * When solving, the program will first solve "logically" as much as possible using two
 * basic techniques (explained in the solveLogically method). During this stage
 * it narrows down possibilities and checks for inconsistencies.
 *
 * If inconsistencies are found, the solution is discarded. Otherwise, if it
 * couldn't be fully solved, it is placed back on the heap.
 *
 * Each time the program takes out a new partial solution from the heap, it
 * chooses the cell with the least possible values and tries all of them. For
 * each possibility, it will assign it to the cell and try solving. It will
 * then discard or place what it gets on the heap as necessary and continue.
 *
 *
 *
 *
 * Details of the the data structure:
 *
 * A graph of cells and zones.
 *
 * Cells refer to the zones they belong to and zones refer to the cells they
 * contain.
 *
 * Each cell keeps track of the possible values it can take.
 * Each zone keeps track of the values that have been previously been found in
 * the zone.
 * 
 * I keep the cells and zones in arrays (allCells and allZones) and refer to
 * them using their index in the array. I do this instead of using references
 * to make it easier to clone the whole data structure.
 * 
 * Additionally, a set called emptyCells contains the ids of all the cells that
 * haven't been assigned a value yet. (The choice of data structure is a bit
 * weird in hindsight. I guess it was to have fast removals. An array of bools
 * or a bitfield would be easy to use instead of this.)
 * 
 *
 * Note that throughout my code, I'll use arrays of size N+1 to contain values
 * indexed from 1 to N with the first position unused.
 */

class Sudoku
{
    /* SIZE is the size parameter of the Sudoku puzzle, and N is the square of the size.  For 
     * a standard Sudoku puzzle, SIZE is 3 and N is 9. */
    int SIZE, N;

    /* The grid contains all the numbers in the Sudoku puzzle.  Numbers which have
     * not yet been revealed are stored as 0. */
    int Grid[][];

    /* Cell
     * Represents an sudoku cell for which we don't know the solution yet.
     */
    class Cell {
        // zones contains the ids of the zones to which this cell belongs
        ArrayList<Integer> zones;
        
        // possibilities represent what values haven't been eliminated for the cell
        // numberOfPossibilities keeps track of the number of possibilites left
        boolean[] possibilities;
        int numberOfPossibilities;
        
        // Even though my algorithm doesn't care about the position of the cell in the grid, it still needs the coordinates
        // to be able to set the right square in the solution grid once the value in this cell is found
        int originalX, originalY;
        
        int id;
        
        // This constructor is used to create the cell at the beginning
        public Cell(int id, int origX, int origY) {
            zones = new ArrayList<Integer>();
            possibilities = new boolean[N+1];
            for (int i = 1; i <= N; i++) {
                possibilities[i]=true;
            }
            numberOfPossibilities = N;
            originalX=origX;
            originalY=origY;
            this.id = id;
        }
        // This constructor is used when copying the sudoku
        public Cell(Cell cell){
            zones = new ArrayList<Integer>(cell.zones);
            possibilities=cell.possibilities.clone();
            numberOfPossibilities = cell.numberOfPossibilities;
            originalX = cell.originalX;
            originalY = cell.originalY;
            id = cell.id;
        }
        
        public void excludePossibility(int value){
            // If this possibility was open before, now there is one less possibility
            if (possibilities[value]){
                numberOfPossibilities--;
            }
            possibilities[value] = false;
        }
    }
    
    /* Zone
     * A zone is a generaliztion of a sudoku square, row, or column.
     * Contrarily to cells, zones are not removed from the structure when they are all used, since I found it makes the
     * program slower, not faster. 
     */
    class Zone {
        // emptyCells contains the ids of the unpromoted cells inside the zone
        // I use a set to rapidly remove elements from it.
        Set<Integer> emptyCells;
        
        // foundNumbers represents the number that were already found in cells
        // of the zone.  Once the cells were promoted, the values they were
        // promoted to are marked here, so they can be later eliminated from
        // the other cells in the zone
        boolean[] foundNumbers;
        
        int id;
        
        // This constructor is used at the beginning.
        public Zone(int id) {
            foundNumbers = new boolean[N+1];
            for (int i = 1; i <= N; i++) {
                foundNumbers[i]=false;
            }
            this.id = id;
            emptyCells = new LinkedHashSet<Integer>();
                    
        }
        
        // This constructor is used when the sudoku is copied.
        public Zone(Zone zone) {
            foundNumbers = zone.foundNumbers.clone();
            id = zone.id;
            emptyCells = new LinkedHashSet<Integer>(zone.emptyCells);
        }
        
        // Used to facilitate adding a cell to the zone at the beginning.
        public void addCell(Cell cell){
            emptyCells.add(cell.id);
            cell.zones.add(this.id);
        }
    }
    
    
    // These will contain all the zones and cells. The positions of the cells and zones in these lists
    // will be their ids.
    ArrayList<Cell> allCells;
    ArrayList<Zone> allZones;
    
    
    // When a new zone or cell is created at the beginning of the algorithm, we need to insert it inside the
    // lists allCells and allZone, and mark them with the correct id. These two functions are used to simplify that.
    
    private Zone createZone(){
        Zone ret = new Zone(allZones.size());
        allZones.add(ret);
        return ret;
    }
    private Cell createCell(int x, int y){
        Cell ret = new Cell(allCells.size(),x,y);
        allCells.add(ret);
        return ret;
    }
    
    // Contains the ids of cells for which we don't know the value yet. Used to iterate over them.
    Set<Integer> emptyCells;
    
    
    // This function is used at the beginning to create the structure that will be used while solving the sudoku
    public void initilizeSolvingStructure(){
        
        // There can be at most N*N cells and 3*N zones (N rows, N columns, and N squares)
        allCells = new ArrayList<Cell>(N*N);
        allZones = new ArrayList<Zone>(3*N);
        
        // There are 3 different types of zones: squares, rows, and columns.
        Zone[] squareZones = new Zone[N];
        Zone[] columnZones = new Zone[N];
        Zone[] rowZones = new Zone[N];
        
        // There are N zones of each type.
        for (int i = 0; i < N; i++) {
            squareZones[i]=createZone();
            columnZones[i]=createZone();
            rowZones[i]=createZone();
        }
 
        // Iterate through through every cell in the grid and put it in the right zones.
        for(int i=0; i<N; i++){
            for(int j=0; j<N; j++){
                int value = Grid[i][j];
                
                // If the value isn't determined create a Cell and add it to the right zones
                if(value==0){
                    Cell cell = createCell(i,j);
                    squareZones[i/SIZE + (j/SIZE)*SIZE].addCell(cell);
                    columnZones[i].addCell(cell);
                    rowZones[j].addCell(cell);
                }
                // Else, write in the right zones that that value was already found.
                else{
                    squareZones[i/SIZE + (j/SIZE)*SIZE].foundNumbers[value]=true;
                    columnZones[i].foundNumbers[value]=true;
                    rowZones[j].foundNumbers[value]=true;
                }
            }
        }
        
        emptyCells = new LinkedHashSet<Integer>();
        
        // add the ids of all the cells into emptyCells
        for (int i = 0, size = allCells.size(); i < size; i++) {
            emptyCells.add(i);
        }
    }
    
    /* This method will try to solve the sudoku logically as far as it can.
     * It will return true if everything was correct, and false if there was some kind of
     * inconsistency and there is a need to backtrack.
     */
    public boolean solveLogically(){
        while (true){
            /*
             * Here, I use two different techniques to solve the sudoku.
             * 
             * a) eliminating what values can go in a cell based on the values of other cells in its zones and
             * promoting the cell if it has only one possibility left
             * b) checking if a cell is the only one that can take a certain value in one of its zones and
             * promoting it then
             *
             * These two techniques correspond roughly to the SiSo and SC techniques described here:
             * http://www.menneske.no/sudoku/5/eng/reducingmethods.html
             *
             * Combining both techniques lets you solve sudokus with very little need for backtracking.
             * For example, for the provided veryHard5x5.txt, no backtracking was needed at all.
             *
             * During these two techniques my algorithm takes note of what cells are to be promoted and only promote
             * them at the end. This is to prevent bugs that can be caused by removing elements during iteration.
             */
            
            // I use a LinkedHashMap so that I can add rapidly, and check if I already tried promoting
            // a certain cell.
            LinkedHashMap<Integer,Integer> cellsToBePromoted = new LinkedHashMap<>();
            
            
            // Using technique a)
            
            // for every empty cell
            for (int cellId : emptyCells) {
                Cell cell = allCells.get(cellId);
                
                // for every zone the cell belongs to
                for (int zoneId : cell.zones){
                    Zone zone = allZones.get(zoneId);
                    
                    // exclude all possibilities that were found in the zone
                    for (int i=1; i<=N; i++){
                        if (zone.foundNumbers[i]){
                            cell.excludePossibility(i);
                        }
                    }
                }
                
                // if a cell has no possibilities, there was an error
                if (cell.numberOfPossibilities==0){
                    return false;
                }
                // if a cell has one possibility, set it to be promoted
                else if (cell.numberOfPossibilities==1){
                    
                    // find that one remaining possibility
                    int value=1;
                    while (!cell.possibilities[value]){
                        value++;
                    }

                    // check if we already tried to promote that cell to some different value
                    Integer objectInSet = cellsToBePromoted.get(cellId);
                    if(objectInSet!=null && objectInSet!=value){
                        return false;
                    }
                    else{
                        cellsToBePromoted.put(cellId, value);
                    }
                }
            }
            
            
            
            // Using technique b)
            
            // for every zone
            for (Zone zone : allZones){
                
                // possibilityCount will count the number of times a possibility appears in the zone
                // lastCellWithPossibility tracks the last cell with such possibility, so that it can be promoted
                int[] possibilityCount = new int[N+1];
                int[] lastCellWithPossibility = new int[N+1];
                
                // initialize possibilityCount
                for (int i=1; i<=N; i++){
                    possibilityCount[i]=0;
                }
                
                // for every cell in zone
                for (int cellId : zone.emptyCells){
                    Cell cell = allCells.get(cellId);
                    
                    // go through all the possibilities of the cell and increment the counts
                    for (int i=1; i<=N; i++){
                        if(cell.possibilities[i]){
                            possibilityCount[i]++;
                            lastCellWithPossibility[i]=cellId;
                        }
                    }
                }
                // check what values can go in only one cell
                for (int i=1; i<=N; i++){
                    if(!zone.foundNumbers[i]){
                        
                        // if no cell in an entire zone can take a value that wasn't yet set as found, we have an error
                        if(possibilityCount[i] == 0){
                            return false;
                        }
                        // if only one cell can take a certain value, it is to be promoted
                        else if(possibilityCount[i] == 1){
                            // check if we have already tried to promote that cell to some different value
                            Integer objectInSet = cellsToBePromoted.get(lastCellWithPossibility[i]);
                            if(objectInSet!=null && objectInSet!=i){
                                return false;
                            }
                            else{
                                // set to be promoted
                                cellsToBePromoted.put(lastCellWithPossibility[i], i);
                            }
                        }
                    }
                }
            }
            
        
            // If no cells have been selected to be promoted, that means that this part of the algorithm
            // has done all that it can.
            if(cellsToBePromoted.size()==0){
                return true;
            }
            
            // Finally, promote the cells that need to be promoted.
            for (Map.Entry<Integer,Integer> info : cellsToBePromoted.entrySet()){
                // promoteCell does a consistency check itself, if it returns false, there was an error
                if(!promoteCell(info.getKey(),info.getValue())){
                    return false;
                }
            }
        }
    }
    
    
    /*
     * This function promotes a cell by setting the right value in the grid and completely removing it from the
     * solving structure.
     * This function returns true if everything went well, and false if there was an inconsistency.
     */
    private boolean promoteCell(int cellId, int value){
        
        Cell cell = allCells.get(cellId);
        
        // for every zone to which the cell belongs
        for (int zoneId : cell.zones){
            Zone zone = allZones.get(zoneId);
            
            // If this number was already found in the zone, there is an error
            if (zone.foundNumbers[value]==true){
                return false;
            }
            // Otherwise, remove the cell from the zone and mark that the value was found in the zone.
            zone.emptyCells.remove(cellId);
            zone.foundNumbers[value]=true;
        }
        
        // Set the value in the grid
        Grid[cell.originalX][cell.originalY]=value;
        
        // removes the cell from the set of empty cells
        emptyCells.remove(cellId);
        
        // I set this to null so that there are less cells to be copied.
        allCells.set(cellId, null);
        return true;
    }

    
    // this function does just that, copy a sudoku, including its solving structure
    private Sudoku copy(){
        
        // create a new sudoku
        Sudoku ret = new Sudoku(SIZE);
        
        // copy the grid
        for (int i=0; i<N; i++){
            for (int j=0; j<N; j++){
                ret.Grid[i][j]= Grid[i][j];
            }
        }
        
        // create arrays for the cells and zones
        ret.allCells = new ArrayList<Sudoku.Cell>(N*N);
        ret.allZones = new ArrayList<Sudoku.Zone>(3*N);
        
        // fill them with cells and zones
        for (Cell cell : allCells){
            if(cell!=null){
                ret.allCells.add(ret.new Cell(cell));
            }
            else{
                ret.allCells.add(null);
            }
        }
        for (Zone zone : allZones){
            if(zone!=null){
                ret.allZones.add(ret.new Zone(zone));
            }
            else{
                ret.allZones.add(null);
            }
        }
        
        // copy the emptyCells array
        ret.emptyCells = new LinkedHashSet<Integer>(emptyCells);
        
        return ret;
    }
    

    /* The method that actually solves the sudoku.
     *
     * It is directly called from main and was used by the evaluators to test out code.
     */
    public void solve(){
        this.initilizeSolvingStructure();
        
        this.solveLogically();
        
        if (this.emptyCells.size()==0){
            return;
        }
        
        // Create a heap to keep the intermediate sudoku states and always work on the one with the least empty cells
        PriorityQueue<Sudoku> heap = new PriorityQueue<Sudoku>(1024, new Comparator<Sudoku>(){
            public int compare(Sudoku a, Sudoku b){
                return a.emptyCells.size() - b.emptyCells.size();
            }
        });
        
        heap.add(this);
        
        while(!heap.isEmpty()){
            final Sudoku sudoku = heap.poll();
            
            // Choose the empty cell with the least open possibilities
            int cellId = Collections.min(sudoku.emptyCells, new Comparator<Integer>() {
                public int compare(Integer a, Integer b){
                    return sudoku.allCells.get(a).numberOfPossibilities - sudoku.allCells.get(b).numberOfPossibilities;
                }
            });
            Cell cell = sudoku.allCells.get(cellId);
            
            // these two will be used to check if we're at the last possibility so that we can prevent a copy
            int lastPossibilityIndex = cell.numberOfPossibilities-1;
            int possibilityIndex = 0;
            
            // For every possibility in the chosen cell
            for (int i=1; i<=N; i++){
                if(cell.possibilities[i]){
                    

                    // Create a copy of the sudoku object or just take the same object if we're at the last possibility.
                    Sudoku newSudoku;
                    if(possibilityIndex==lastPossibilityIndex){
                        newSudoku = sudoku;
                    }
                    else{
                        newSudoku = sudoku.copy();
                    }
                    
                    // Try to promote the cell to the chosen possibility and then solving logically.
                    // Both of these methods should return true if there was no inconsistency.
                    if(newSudoku.promoteCell(cellId, i) && newSudoku.solveLogically()){
                        
                        // If there are no more empty cells, we're done.
                        if(newSudoku.emptyCells.size()==0){
                            this.Grid = newSudoku.Grid;
                            return;
                        }
                        // Else, put it on the heap
                        else{
                            heap.add(newSudoku);
                        }
                    }
                    
                    possibilityIndex++;
                }
            }
        }
    }


    /* 
     *  The following functions were provided by the provided starting code.
     *
     *
     */

    /*****************************************************************************/
    /* NOTE: YOU SHOULD NOT HAVE TO MODIFY ANY OF THE FUNCTIONS BELOW THIS LINE. */
    /*****************************************************************************/
 
    /* Default constructor.  This will initialize all positions to the default 0
     * value.  Use the read() function to load the Sudoku puzzle from a file or
     * the standard input. */
    public Sudoku( int size )
    {
        SIZE = size;
        N = size*size;

        Grid = new int[N][N];
        for( int i = 0; i < N; i++ ) 
            for( int j = 0; j < N; j++ ) 
                Grid[i][j] = 0;
    }


    /* readInteger is a helper function for the reading of the input file.  It reads
     * words until it finds one that represents an integer. For convenience, it will also
     * recognize the string "x" as equivalent to "0". */
    static int readInteger( InputStream in ) throws Exception
    {
        int result = 0;
        boolean success = false;

        while( !success ) {
            String word = readWord( in );

            try {
                result = Integer.parseInt( word );
                success = true;
            } catch( Exception e ) {
                // Convert 'x' words into 0's
                if( word.compareTo("x") == 0 ) {
                    result = 0;
                    success = true;
                }
                // Ignore all other words that are not integers
            }
        }

        return result;
    }


    /* readWord is a helper function that reads a word separated by white space. */
    static String readWord( InputStream in ) throws Exception
    {
        StringBuffer result = new StringBuffer();
        int currentChar = in.read();
    String whiteSpace = " \t\r\n";
        // Ignore any leading white space
        while( whiteSpace.indexOf(currentChar) > -1 ) {
            currentChar = in.read();
        }

        // Read all characters until you reach white space
        while( whiteSpace.indexOf(currentChar) == -1 ) {
            result.append( (char) currentChar );
            currentChar = in.read();
        }
        return result.toString();
    }


    /* This function reads a Sudoku puzzle from the input stream in.  The Sudoku
     * grid is filled in one row at at time, from left to right.  All non-valid
     * characters are ignored by this function and may be used in the Sudoku file
     * to increase its legibility. */
    public void read( InputStream in ) throws Exception
    {
        for( int i = 0; i < N; i++ ) {
            for( int j = 0; j < N; j++ ) {
                Grid[i][j] = readInteger( in );
            }
        }
    }


    /* Helper function for the printing of Sudoku puzzle.  This function will print
     * out text, preceded by enough ' ' characters to make sure that the printint out
     * takes at least width characters.  */
    void printFixedWidth( String text, int width )
    {
        for( int i = 0; i < width - text.length(); i++ )
            System.out.print( " " );
        System.out.print( text );
    }


    /* The print() function outputs the Sudoku grid to the standard output, using
     * a bit of extra formatting to make the result clearly readable. */
    public void print()
    {
        // Compute the number of digits necessary to print out each number in the Sudoku puzzle
        int digits = (int) Math.floor(Math.log(N) / Math.log(10)) + 1;

        // Create a dashed line to separate the boxes 
        int lineLength = (digits + 1) * N + 2 * SIZE - 3;
        StringBuffer line = new StringBuffer();
        for( int lineInit = 0; lineInit < lineLength; lineInit++ )
            line.append('-');

        // Go through the Grid, printing out its values separated by spaces
        for( int i = 0; i < N; i++ ) {
            for( int j = 0; j < N; j++ ) {
                printFixedWidth( String.valueOf( Grid[i][j] ), digits );
                // Print the vertical lines between boxes 
                if( (j < N-1) && ((j+1) % SIZE == 0) )
                    System.out.print( " |" );
                System.out.print( " " );
            }
            System.out.println();

            // Print the horizontal line between boxes
            if( (i < N-1) && ((i+1) % SIZE == 0) )
                System.out.println( line.toString() );
        }
    }


    /* The main function reads in a Sudoku puzzle from the standard input, 
     * unless a file name is provided as a run-time argument, in which case the
     * Sudoku puzzle is loaded from that file.  It then solves the puzzle and
     * outputs the completed puzzle to the standard output.
     *
     * It is assumed that the sudoku has a solution and will output whatever solution it finds first.
     */
    public static void main( String args[] ) throws Exception
    {
        InputStream in;
        
        if( args.length > 0 ) 
            in = new FileInputStream( args[0] );
        else
            in = System.in;
        

        // The first number in all Sudoku files must represent the size of the puzzle.  See
        // the example files for the file format.
        int puzzleSize = readInteger( in );
        if( puzzleSize > 100 || puzzleSize < 1 ) {
            System.out.println("Error: The Sudoku puzzle size must be between 1 and 100.");
            System.exit(-1);
        }

        Sudoku s = new Sudoku( puzzleSize );

        // read the rest of the Sudoku puzzle
        s.read( in );

        
        // Solve the puzzle.  We don't currently check to verify that the puzzle can be
        // successfully completed.  You may add that check if you want to, but it is not
        // necessary.
        long oldTime = System.currentTimeMillis();
        
        s.solve();
        
        long elapsedTime = System.currentTimeMillis()-oldTime;
        
        // Print out the (hopefully completed!) puzzle
        s.print();
        System.out.println("Time spent solving: "+elapsedTime+" milliseconds.");
    }
}

