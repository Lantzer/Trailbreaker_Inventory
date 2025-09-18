# Trailbreaker Application Requirements

## Home Screen

1. Dashboard with buttons for different pages
    - Fermentor Tanks
    - Bright Tanks
    - Current Inventory Page
    - Outgoing Inventory
    - Cooler Inventory Management


## Pages

1. Fermentor Tanks
    - List Tanks with quick info. (Ferm #, Contents, Quantity, Date pressed)
    - On select, open detail page.
        - Fermentor Batch #
        - Notes (yeast/lysosyme dates, misc notes on apples input)
        - Fermentor processing list
            - Ex. Processed 400 gallons into bright tank # 2 on 01/10/25
        - Button to jump to process from page?
        - Option to empty Fermentor
            - Snapshots fermentor details and stores for future reference
            - Clears fermentors info back to empty
    - handle scenario where we sell HC to other businesses, or move to bucket for kitchen


2. Bright Tanks
    - List Tanks with quick info. (Tank #, Title, Quantity, Date processed)
    - On select, open detail page
        - Notes (Cider flavor, changed to another flavor)
        - Keg/Canning List
            - Ex. Kegged (16) 1/2 bbls on 01/15/25
            - Canned 
        - Processing button
        - Move batch button
        - Modifiy remaining option (prompt for new flavor, quantity (default to remaining quantity))
            - Stores snapshot of original batch as if it was finished, with reference to continuation batch #. Continuation Batch #
            - Modifying creates new batch number and references previous. Modifying Batch #
            - Keeps relevant notes and history
    - List of things added to tank
        - Hard Cider (+ Batch #)
        - Fresh Juice
        - Sulfites

            Example Record Flow:
                Bright Tank #2 contains 400 gallons of cider.
                User kegs 200 gallons (recorded in Keg/Canning List).
                User selects "Modify Remaining" for the other 200 gallons.
                App creates a new batch (e.g., Batch #124), notes "Continuation from Batch #123, added fruit puree."
                Both batches are linked for audit/history.

3. Current Inventory
    - Display all current inventory items in the database
        - Filter by flavor, size, quantity, batch date
        - Sorted by Flavor & Batch Date
    - Selecting an Item opens details page
        - All details above (Future note: show locations in keg cooler)

4. Outgoing Inventory
    - Select where order is going. (Odom, Outside Sale, Donation, Waste)
    - Open outgoing order form
        - Odom
            - Select Kegs (correct flavor, size, date)
            - Outgoing date
            - Picture
        - Outside Sale
            - Who
            - Select Kegs (correct flavor, size, date)
            - Outgoing date
        - Donation
            - Who
            - Select Kegs (correct flavor, size, date)
            - Outgoing date
        - Waste
            - Select Kegs (correct flavor, size, date)
            - Reason
            - Date
    
5. Cooler Inventory Management
    Page for handling weekyl Tasting Room Inventory restocking, and keg transfers
    - UI should display Keg Cooler shelves and their stock at a glance
    - Ability to reorganize keg locations
    - Search a flavor and display shelf numbers, keg size, quantity, and date of keg. (default show oldest kegs first)
    - Keg Transfers
    TR Inv:
        - Want to remove items from a shelf and add to "temp holding location".
        - Want to be able to move kegs from different shelves
        - When finished with TR Inventory, finalize and remove items from temp holding locations, making a record of inventory added to TR cooler
    Keg Transfers:
        - Want to select kegs to use for keg transfers
        - Place them in "keg transfer" cooler location
            - Sometimes when taking kegs down, they are marked for keg transfers, not immediately transferred though
            - leave note what they are for?
        - Keg transfer will be its own location in cooler inventory
            - When selecting a keg, open a page to enter transfer to data:
                - Keg size, Flavor, date
                - Similar to bright tank batches, keep record of previous cider batch # & other details.
                - Want to be able to only use partial of a keg, allowing for the rest to be added to TR if needed.
                - When finished transferring, remove kegs transferred out of, add kegs transferred into in inventory database.
                - Create a record of the transfer